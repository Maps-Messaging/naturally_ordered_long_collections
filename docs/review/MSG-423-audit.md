# MSG-423: signed-long collections implementation and test audit

Date: 2026-11-01  
Repository: `Maps-Messaging/naturally_ordered_long_collections`  
Review baseline: `development` commit `fb6ccc25209fcaffbc3e566d721901f4f9750839`  
Work branch: `fix/MSG-423-signed-long-audit`

## 1. Correction to the initial incident report

The original issue report incorrectly treated source from the repository's default branch as if it were the current `development` implementation. The first four claims in MSG-423 were **not verified as present in development**. The actual development baseline already contains:

- `BitSetFactory.getStartIndex()`: negative-window-aware `Math.floorMod` handling and signed-edge guards;
- `NaturalOrderedLongQueue`: nullable `poll()/peek()` results, with `-1L` treated as data, and `element()/remove()` exceptions on empty;
- `FileBitSetFactoryImpl`: version 2 records with explicit allocated/free state, a legacy migration path and signed unique IDs;
- `OffsetBitSet`: nullable absolute search results and signed window boundaries;
- dedicated signed-domain, persistence, negative-ID and concurrency tests.

This is a **review/provenance failure**, not evidence that these four defects still exist in the current branch. The snapshot used by server Jenkins #306 was `2.0.0-20261025.003243-2`. Its exact bytecode and source provenance have **not** been independently compared. Do not attribute the server's intermittent CoAP Observe failure to this library without reproduction.

## 2. Production-source inventory

The following production types were inspected by source retrieval, contract/path inspection and targeted review (not a substitute for line-by-line dynamic verification):

| Area | Types |
| --- | --- |
| Core collections | `NaturalOrderedCollection`, `NaturalOrderedLongQueue`, `NaturalOrderedLongList`, `ConcurrentNaturalOrderedLongQueue` |
| Priority collections | `PriorityCollection`, `PriorityQueue`, `PriorityEntry`, `PriorityFactory` |
| Audit and mapping | `AuditedNaturalOrderedLongQueue`, `AuditWriter`, `MappedBufferHelper` |
| Bitsets and iteration | `BitSet`, `BitSetImpl`, `ByteBufferBackedBitMap`, `BitSetIterator`, `BitSetListIterator`, `BitWiseOperator`, `OffsetBitSet`, `DelegatingOffsetBitSet` |
| Factory contracts | `BitSetFactory`, `BitSetFactoryImpl`, `ByteBufferBitSetFactoryImpl`, `ManagedBitSetFactoryImpl` |
| Persistence and allocation | `FileBitSetFactoryImpl`, `FileOffsetBitSet`, `SharedFileBitSetFactoryImpl`, `ConcurrentSharedFileBitSetFactoryImpl`, `EphemeralFileBitSetFactoryImpl`, `HybridBitSetFactoryImpl` |

## 3. Newly discovered findings register

Findings below are all discoveries recorded during this review. **No production correction for these additional findings has been authorised.** Each remains for explicit disposition. "Confirmed" means the supplied source directly contradicts its stated Java API contract or clearly demonstrates the indicated behaviour; it does not claim a runtime test was executed.

| ID | Status / severity | Source | Finding and reproduction | Missing or inadequate coverage / proposed decision |
| --- | --- | --- | --- | --- |
| F-01 | Confirmed / Medium | `NaturalOrderedCollection.toArray(T[])`, lines 129-135 | Stops at the supplied array length, silently truncating collections when the array is too small. When the array is larger, does not set the first unused element to null. This violates `Collection.toArray(T[])`. | Existing `NaturalOrderedLongQueueTest.typedToArrayWithLargerArrayLeavesTrailingEntriesUnchanged` actually asserts the wrong behaviour. Decide whether to correct public contract and tests. |
| F-02 | Confirmed / Medium | `NaturalOrderedCollection.LongIterator.next()`, lines 409-414; `NaturalOrderedLongList.LongListIterator.next()/previous()`, lines 140-145, 172-177 | Following an exhausted `hasNext()/hasPrevious()`, methods return null rather than throwing `NoSuchElementException`. | Add exhaustion and iterator-contract tests if correction approved. |
| F-03 | Confirmed / High | `PriorityCollection.PriorityCollectionIterator.remove()`, lines 370-375 | Removes from the underlying queue without decrementing `entryCount`. A single-element collection can have a removed entry while `isEmpty()` and `contains()` consult stale count until `size()` recalculates. | Add post-iterator-remove `isEmpty/contains/size` assertions; approve bookkeeping correction. |
| F-04 | Confirmed / Medium | `PriorityCollection.addAll(Collection)`, lines 188-205 | With a `PriorityCollection` right-hand side, returns true even when no element is added. With an ordinary collection, returns false early on the first duplicate, even if other elements were already added. Conflicts with `Collection.addAll` changed-state semantics. | Add zero/duplicate/mixed tests for both overload and input types; approve changed-return correction. |
| F-05 | Design contract issue / Medium | `NaturalOrderedLongList.addAll(int, Collection)`, lines 42-44 | Ignores the index despite implementing `List`. Index-based operations elsewhere deliberately throw `UnsupportedOperationException`; this method does neither. | Decide whether to reject indexed insertion or explicitly document a deliberate nonstandard contract. |
| F-06 | Confirmed test execution gap / High | `PriorityQueuePeekFailureReproTest`, method at line 50; `ForcedReleaseRaceTest`, method at line 43 | Both contain plausible reproduction methods with **no JUnit `@Test` annotation**. Surefire includes `*Test.java`, but it will not execute these methods. Enabling them without an agreed expected outcome may introduce intentionally failing/racy tests. | Decide whether to convert them to deterministic assertions of supported behaviour, move to explicit diagnostic tools, or annotate as deliberately disabled with explanation. |
| F-07 | Concurrency risk / High pending reproduction | `ConcurrentNaturalOrderedLongQueue.iterator()`, lines 133-135, and `NaturalOrderedCollection.LongIterator` | A synchronized `iterator()` call returns an iterator whose subsequent operations run outside the lock over mutable structures and may see released bitsets. `ForcedReleaseRaceTest` appears intended to reproduce related behaviour. | Define iterator consistency contract (snapshot, weakly consistent, or externally synchronized) and test against concurrent remove/release before changing implementation. |
| F-08 | Risk / Medium pending reproduction | `NaturalOrderedLongQueue.poll()`, lines 55-72 | Empty windows are removed by recursive `return poll()`. A large number of empty physical windows can grow recursion depth. | Prove whether such windows can exist at scale with each factory; consider iterative drain only if demonstrated. |
| F-09 | Safety/compatibility review / Medium | `MappedBufferHelper.closeDirectBuffer()` | Reflection-based `Unsafe.invokeCleaner` failures are silently ignored, leaving deterministic unmapping unproven on all supported JDKs. | Test JDK 25 mapped-file close/delete and document supported fallback, without an unapproved deployment or runtime change. |
| F-10 | Confirmed test-isolation defect / Medium; runtime cause not proven | `PriorityCorruptionTest.testCorruption()` | The test used a fixed `./testCorruption.bin` shard-file base across runs and did not close its `SharedFileBitSetFactoryImpl`. On 1 November 2026, a local `mvn clean test` reached the v2 loader and failed reading a 1,584-byte shard (`Invalid version 2 bitset file length 1584`), before any priority assertions. This is consistent with an incompatible leftover file but does **not** establish what originally wrote it. | Scoped correction on the MSG-423 branch uses `@TempDir` and closes the shared factory without weakening assertions or the v2 format validator. Rerun on a clean environment and, if it still fails with isolated files, investigate an actual writer/persistence defect. |

These findings are distinct from the four originally alleged signed-long defects that are already addressed on `development`.

## 4. Test review and coverage

### Coverage present on development

- `SignedLongDomainTest` (5 tests), `SignedLongHappyPathTest` (4), `SignedLongBadPathTest` (6), `SignedLongMurphyPathTest` (6), and `SignedLongFactoryIntegrationTest` (6) exercise signed extrema, negative boundaries, `-1L`, persistence migration, factories and concurrent operations.
- `NaturalOrderedLongQueueTest` includes explicit `-1L` and queue-contract tests; `NaturalOrderedLongListTest`, `PriorityCollectionTest`, `PriorityQueueTest`, and `ConcurrentNaturalOrderedLongQueueTest` exercise ordinary operations.
- Bitset tests exist for boundary parity, heap/direct backends, offset maps, factory lifecycle, file persistence, signed IDs, hybrid migration and shared shards.
- Surefire includes `**/*Test.java` and `**/*IT.java`; no global `skipTests` declaration was found in the inspected POM. Test discovery still requires verification using actual Surefire XML and JUnit execution.

### Gaps

- F-06 reproduction methods are not executable JUnit test cases.
- F-01 has a test that enshrines a violation of the Java collection contract.
- Iterator exhaustion, priority iterator removal bookkeeping, `PriorityCollection.addAll` return semantics and concurrent iterator lifecycle are not adequately demonstrated by the tests inspected.
- No test results, class-by-class JaCoCo report, mutation score, or confirmation of the exact Jenkins-consumed library JAR were available in this review environment.

### Test addition on MSG-423

`SignedLongReferenceModelAuditTest` adds reproducible, seeded `TreeSet<Long>` model checks across window sizes `1,3,7,63,64,65,257,8192` for heap and direct-buffer factories. The operation sequence covers `offer/remove/poll/peek/element/contains`, extrema, negative window neighbours, duplicates and iteration. It is **not yet executed**; passing source review is not test evidence.

## 4a. Scoped corrections following the 506-test baseline

The user reported a local `mvn clean test` result of **506 tests, 0 failures, 0 errors, 0 skips** after the isolated shared-file fixture correction. This result **predates** the following changes; no post-correction Maven result has been supplied.

**High-frequency invariant (explicit instruction):** When the final bitset window in a queue becomes empty during polling or individual removal, retain that window for reuse. When another window remains, empty windows may be released. Do not replace this with unconditional allocation/release or add snapshot allocations to the server's hot iterator path.

| Finding | Current correction status | Scope and evidence still required |
| --- | --- | --- |
| F-01 | Code and regression tests pushed; **unverified** | `NaturalOrderedCollection.toArray(T[])` and `PriorityCollection.toArray(T[])` now respect destination array sizing and the null terminator. Existing incorrect larger-array test corrected. |
| F-02 | Code and regressions pushed; **unverified** | Collection/list/bitset iterators now throw on exhaustion; bitset iterator removal rejects repeated calls. Additional iterator conformance under random operation sequences remains. |
| F-03 | Code and regression pushed; **unverified** | Priority iterator removal decrements `entryCount`, so `isEmpty` and `contains` do not require a prior `size()` to repair count. |
| F-04 | Code and regressions pushed; **unverified** | Priority bulk add/remove changed-state results corrected; copying from a differently-sized priority layout now throws explicitly instead of silently truncating. |
| F-05 | Code and existing tests updated; **unverified** | `NaturalOrderedLongList.addAll(index,values)` rejects indexed insertion consistently with the other unsupported indexed operations. This is a visible contract change; validate downstream callers before release. |
| F-06 | **Partially addressed** | Previously unannotated `PriorityQueuePeekFailureReproTest` is now a JUnit test with explicit exception assertion. `ForcedReleaseRaceTest` remains a non-deterministic diagnostic method, not an executable JUnit test. Its concurrency semantics are a decision under F-07, not silently enabled. |
| F-07 | **Unresolved; explicit design decision required** | A synchronized `iterator()` factory method does not guard subsequent iterator operations against release. Fixing this through a snapshot would add an O(n) allocation on a potentially high-frequency server path, contrary to the explicit performance constraint. Define external-lock vs snapshot contract and verify before implementation. |
| F-08 | Code and regressions pushed; **unverified** | `NaturalOrderedLongQueue.poll()` now uses iteration instead of recursion, while retaining the last empty window and releasing an empty window only if another exists. `NaturalOrderedCollection.remove(Object)` preserves the same final-window reuse invariant. |
| F-09 | **Unresolved; platform evidence required** | Deterministic buffer unmapping uses JVM-internal reflection. Turning a failed unmap into a fatal exception or adding a new resource-management strategy could alter running-server error handling; first verify behaviour on supported JDKs and define desired failure policy. |

**Important additional contract conflict:** The user requested **no special `-1` return**. The signed-`long` queue API uses `null` for empty and treats `-1L` as data. However, `PriorityCollection.removeAndGetPriority()` still returns primitive `-1` for missing priority; the server's `BaseMessageStateManager.rollback()` explicitly relies on that sentinel. Internal integer bitset searches also use `-1` to indicate no local (nonnegative) bit index. Changing these contracts requires coordinated API and consumer decisions; no breaking change has been made silently.

**Additional source-level findings reported for disposition, not included in F-01–F-09 correction scope:** A stale inactive source entry in `NaturalOrderedCollection.internalAddAll()` can remove the destination's existing window; `HybridBitSetFactoryImpl.get()` returns raw backing bitsets whereas `release()` handles only delegating wrappers; `DelegatingOffsetBitSet.getBitSet()` is inherited and can reference pre-migration backing; legacy file migration truncates and rewrites the sole source file in place; `ManagedBitSetFactoryImpl.release()` relies on equality based only on window start; and `NaturalOrderedLongList` implements `List` without implementing the usual list equality semantics. These are source-level concerns needing focused reproduction, impact classification and explicit decisions before additional edits.

## 5. Verification status and required gates

- [x] Confirm `development` baseline SHA and create MSG-423 branch.
- [x] Inspect core/factory/persistence code and signed-domain regression-test definitions.
- [x] List newly discovered contract and coverage findings without making unapproved production changes.
- [x] Add independently seeded reference-model test source.
- [x] Inspect user-supplied local `mvn clean test` log (1 November 2026): 506 tests executed, 0 assertion failures, 1 test error (`PriorityCorruptionTest.testCorruption`, pre-assertion invalid v2 file length), 0 skipped. The two `SignedLongReferenceModelAuditTest` tests passed. `ACCESS_AFTER_CLOSE` warnings occur in expected `assertThrows()` tests.
- [ ] Rerun local `mvn clean test` after the MSG-423 `PriorityCorruptionTest` isolation change, then `mvn --batch-mode --no-transfer-progress clean verify`; capture Surefire counts and JaCoCo coverage. This execution container has JDK 21 but no Maven; the local result was supplied by the user, and a successful post-fix build is **not claimed**.
- [ ] Reproduce each additional finding before deciding a production fix, with explicit user disposition under the change-scope policy.
- [ ] Validate persistence migration with stored fixtures and the exact `2.0.0-20261025.003243-2` binary consumed by Jenkins.
- [ ] Run server retained/subscription/delayed/recovery integration with the agreed library artifact and JDK 25.
- [ ] Perform a final line-by-line source and test audit and a changed-line scope check before declaring MSG-423 complete.

No claim of runtime compatibility, readiness for release, or CoAP root cause is made by this document.

## Follow-up: server/storage removal lifecycle review (2026-11-01)

**Baseline:** Current `fix/MSG-423-signed-long-audit` branch. This entry records the source-level correction and regression coverage; it does not assert a passing post-change Maven/JDK 25 run.

### Observed real consumers

- MapsMessaging server `BaseMessageStateManager`: subscription queues use 11 priority levels; `register`, `allocate`, `commit`, `expired`, rollback, and `getAll` exercise queue add/remove/peek and copying. Subscription cleanup `DestinationSubscriptionManager.scanForInterest()` uses `removedQueue.removeAll(interested)` to remove matching windows.
- Server `DestinationStateManagerFactory`: non-persistent subscription state uses `BitSetFactoryImpl(4096)`; persistent state uses `ConcurrentSharedFileBitSetFactoryImpl(..., 4, 4096)`.
- `Maps-Messaging/dynamic_storage`: `MemoryStorage` and `PartitionStorage` construct natural-ordered lists/queues for key enumeration, expiry and eviction; `MemoryTierStorage` uses a natural-ordered queue to track tier migration.

### Approved behaviour

1. Individual and bulk removals must leave *at most one* empty bitset in the collection; when the last window is emptied, retain it for reuse. Explicit `clear()` and `close()` retain their original full-release semantics.
2. Every window actually released by a collection must be removed from that collection's tree in the same path. No periodic or per-event `validateTree()` scan is permitted to compensate.
3. `removeAll(this)` must not mutate its tree through a separate iterator; `retainAll(this)` must be a no-op. Report `Collection` changed-state booleans based on removed values, not internal cache cleanup.
4. Iterator removal targets the last value returned by `next()`/`previous()` even if `hasNext()` moves the active window. The returned value, including `-1L`, is data rather than a sentinel.

### Corrected paths and tests

`NaturalOrderedCollection.removeMatching()`, `removeMatchingBitSet()`, `matchingRetainAll()`, and `LongIterator.remove()` plus `NaturalOrderedLongList.LongListIterator.remove()` were updated. No external invalidation recovery, iterator snapshots, new dependency, persistent file format, or full-tree validation was introduced.

`MSG423RemovalLifecycleTest` adds 12 new regression scenarios: self bulk removal and retain, matching peer removal/retain, empty-cache no-change semantics, plain retain and `removeIf`, iterator removal across window boundaries, list iterator removal, file-backed shared-factory close/reopen with negative persistent IDs, a 1,000,001-event bulk removal, a file-backed iterator/removeIf/retainAll reopen test, and a seeded 2,000-step `TreeSet<Long>` model for heap and direct-backed queues. These are **test definitions**, not yet verified executed results.

### Out-of-scope consumer issue requiring explicit decision

In `Maps-Messaging/dynamic_storage/src/main/java/io/mapsmessaging/storage/impl/tier/memory/MemoryTierStorage.java` the tier-eviction loop accepts a polled key only when `key != null && key != -1`. The collections library supports `-1L` as a legitimate event/key ID; this consumer will skip migrating that key when its queue returns `-1L`. This does not authorize a cross-repository change. Discuss separately; do not silently alter or create a new issue.

### Verification gates before merge/release

- [ ] Local clean JDK 25 `mvn clean test` and `mvn clean verify`, with Surefire totals, skips, and coverage examined after the new commit(s).
- [ ] Validate concurrent shared-file tests and the newly added persistent reopen test on the same filesystem/JDK family used for server production.
- [ ] Run server subscription interest cleanup, retained/in-flight rollback, and storage expiry/tiering integration with a library artifact built from this exact branch.
- [ ] Separate out-of-scope data correctness issues from MSG-423 and obtain a disposition.
