# MSG-423: mapped-bitset integration and event-path performance verification

**Scope:** `naturally_ordered_long_collections` only. Server unit/integration tests and `dynamic_storage` are deliberately excluded from this round. This document records executable test source and benchmark instructions, **not** measured performance or production-compatibility certification.

## 1. Bitset file and mapped-memory integration

`MSG423MappedStorageIntegrationTest` exercises the existing APIs and version-2 file format at the server's actual **4096-bit window** size:

| Scenario | Verifiable invariant |
| --- | --- |
| Signed bounds, separate unique IDs, close/reopen | Values including `Long.MIN_VALUE`, `-1L` and `Long.MAX_VALUE` persist; independently owned collections cannot contaminate one another |
| Release/reuse across unique IDs | A released mapped record is reused without growing the file or inheriting stale bits |
| Bulk OR, AND NOT and AND on mapped bitsets | Correct set operations, source preservation and durable results after reopen |
| Concurrent shared-file factory with four shards | Distinct owners `-1L` and `3L` in the same shard remain isolated, even after releases and restart |
| Direct-buffer vs file-mapped bulk operations | Correct interoperability across word and window boundaries |
| Wrong window size on reopen | Rejected, with the original file bytes unchanged |
| Truncated version-2 record | Rejected without further truncation or rewriting |
| Complete clear, close and reuse of same filesystem path | Mapping lifecycle allows the empty file to be removed and a new file created at the same path |

Existing tests also cover legacy positive-domain record migration (`FileBitSetSignedPersistenceTest`), byte/word boundary parity (`BitSetBoundaryParityTest`), free-state metadata (`FileBitSetFactoryImplTest`) and signed factory integration (`SignedLongFactoryIntegrationTest`).

**To run on JDK 25** (the project's Maven compiler target remains Java 17):

```bash
mvn -Dtest=MSG423MappedStorageIntegrationTest,MSG423RemovalLifecycleTest,FileBitSetSignedPersistenceTest,FileBitSetFactoryImplTest,BitSetBoundaryParityTest,SignedLongFactoryIntegrationTest test
mvn clean verify
```

Use `@TempDir` fixtures, not production state files. A clean reopen verifies ordinary shutdown/restart. **It does not prove durability across process kills, power loss, or concurrent writers on a shared filesystem.** Those are separate tests and failure-policy decisions.

## 2. Repeatable performance workloads

`MSG423QueueWorkloadJMH` is a dedicated JMH benchmark, not a Surefire test; it will not run during `mvn test`.

| JMH method | Operations in one invocation | Purpose |
| --- | --- | --- |
| `addRemoveZeroAndOne` | 2 adds + 2 deletes | Repeated hot-window reuse |
| `monotonicSingleEventAddRemove` | 1 add + 1 delete with monotonically increasing IDs | Allocation churn at 4096/8192/... boundaries |
| `addMillionThenRemoveMillion` | 1,000,001 adds, then 1,000,001 individual removals | Mass backlog lifecycle and removal cost |
| `addMillionThenPollMillion` | 1,000,001 adds, then 1,000,001 ordered polls | Comparison with queue-consumer draining |

Each benchmark runs independently on `heap`, `direct`, `mapped` (single file-backed factory), and `shared` (4-shard concurrent mapped factory). Window size defaults to 4096 bits. `@OperationsPerInvocation` makes JMH report **logical add/delete operations per second**, not just entire million-event invocations per second.

### Compile and run (macOS/Linux shell, JDK 25)

```bash
mvn -DskipTests test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/msg423-jmh-classpath.txt

CP="target/test-classes:target/classes:$(cat target/msg423-jmh-classpath.txt)"

java -cp "$CP" org.openjdk.jmh.Main '.*MSG423QueueWorkloadJMH.*' \
  -bm thrpt -p backend=heap,direct -f 1 -t 1 -wi 1 -i 3 -w 2s -r 5s \
  -prof gc -rf json -rff target/msg423-heap-direct.json

java -cp "$CP" org.openjdk.jmh.Main '.*MSG423QueueWorkloadJMH.*' \
  -bm thrpt -p backend=mapped,shared -f 1 -t 1 -wi 1 -i 3 -w 2s -r 5s \
  -prof gc -rf json -rff target/msg423-mapped-shared.json
```

For focused diagnosis, filter to a method with e.g. `'.*MSG423QueueWorkloadJMH.monotonicSingleEventAddRemove'`, and select one backend with `-p backend=mapped`. For latency, repeat with `-bm avgt`. Keep JMH's default fork isolation and record OS, filesystem, CPU, memory, JVM flags, heap size, JDK build, git SHA and whether results were run on AC power.

**Important comparability limits:** JVM/GC allocation statistics from `-prof gc` do not measure mapped-file disk I/O or durable commits. These methods intentionally use the existing APIs (including boxed `Long` IDs), and file-backed timings include OS page cache, memory mapping and metadata updates. The benchmark does not call `FileChannel.force` or emulate power loss.

### Recording results

| Backend | Same-region ops/s | Monotonic ops/s | Million add/remove ops/s | Million add/poll ops/s | Alloc B/op | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Heap | Pending | Pending | Pending | Pending | Pending | |
| Direct | Pending | Pending | Pending | Pending | Pending | |
| Mapped | Pending | Pending | Pending | Pending | Pending | |
| Shared mapped | Pending | Pending | Pending | Pending | Pending | |

Pre-existing JUnit allocation-count tests already assert 245 opens and 244 releases over IDs 0 through 1,000,000 inclusive, and single-window reuse within one region; they are **regressions**, not elapsed-time benchmarks.

## 3. Decisions requiring explicit user disposition

The points below were identified by source review. They are **not authorised fixes** and were not changed by this test/benchmark work.

| Decision | Issue / evidence | Suggested disposition |
| --- | --- | --- |
| **A. Crash-safe persistence** | `FileBitSetFactoryImpl.open()` marks a reused record allocated before resetting the payload. Legacy migration truncates/rebuilds the same file. Clean close/reopen testing cannot establish power-loss atomicity | Decide whether crash-interruption durability is part of the required guarantee. If yes, first design reproducible fault-injection tests and recovery policy, then approve any format/order change separately |
| **B. Hybrid migration and ownership** | `HybridBitSetFactoryImpl.get()` returns backing instances rather than the delegates expected by `release()`; `DelegatingOffsetBitSet.getBitSet()` can retain pre-migration backing | Determine whether hybrid is a supported runtime backend; if yes, prioritise targeted reproduction and an authorised correction |
| **C. Managed factory identity** | `ManagedBitSetFactoryImpl.release()` uses list removal based on window-start equality, not object identity | Add a targeted collision/ownership test before deciding on production changes |
| **D. Iterator concurrency contract** | `ConcurrentNaturalOrderedLongQueue.iterator()` synchronizes creation but not subsequent access. Deliberate external invalidation was accepted as caller misuse, but concurrent use of supported queue APIs is a separate question | Specify whether callers must hold the queue lock while traversing or require another consistency model. Avoid adding snapshots/allocations without permission |
| **E. Public API semantics** | `NaturalOrderedLongList` implements `List` but lacks standard list equality and indexed retrieval; `PriorityCollection.removeAndGetPriority()` uses `-1` to signal not-found | Decide whether these are documented, deliberately limited APIs or should be redesigned with consumer compatibility |
| **F. Consumer `-1L` collision** | In separate `dynamic_storage`, `MemoryTierStorage` checks `key != null && key != -1` after polling, excluding a valid signed key | Decide separately whether to authorise a storage-repository correction. No change or Jira was created |
| **G. Open/add error path** | The final cached empty window may be released before opening the next window succeeds | Decide whether preserving cache on an allocation/IO error is required; not a data-loss claim on current normal path |

We already agreed **not** to compensate for callers deliberately invalidating an exposed bitset behind the collection. That accepted risk does not justify restoring `validateTree()` on the server's high-frequency path.

## 4. Verification record

- **Last reported green test suite:** 548 tests, failures 0, errors 0, skipped 0. This run preceded the additional storage integration tests.
- **Current additions:** 8 JUnit mapped-storage integration tests plus 4 JMH workloads across four backends.
- **Post-change JDK 25 tests:** Pending execution.
- **JMH throughput and allocation measurements:** Pending execution.
- **Production source, persistence format, server and storage repositories:** Unchanged in this work.
