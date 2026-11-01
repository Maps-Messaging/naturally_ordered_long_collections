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

## 5. Verification status and required gates

- [x] Confirm `development` baseline SHA and create MSG-423 branch.
- [x] Inspect core/factory/persistence code and signed-domain regression-test definitions.
- [x] List newly discovered contract and coverage findings without making unapproved production changes.
- [x] Add independently seeded reference-model test source.
- [ ] Run `mvn --batch-mode --no-transfer-progress clean verify` on the exact branch and inspect Surefire test counts, failures, skips and JaCoCo coverage. The present execution container has JDK 21 and no Maven; **no Maven/JUnit execution is claimed**.
- [ ] Reproduce each additional finding before deciding a production fix, with explicit user disposition under the change-scope policy.
- [ ] Validate persistence migration with stored fixtures and the exact `2.0.0-20261025.003243-2` binary consumed by Jenkins.
- [ ] Run server retained/subscription/delayed/recovery integration with the agreed library artifact and JDK 25.
- [ ] Perform a final line-by-line source and test audit and a changed-line scope check before declaring MSG-423 complete.

No claim of runtime compatibility, readiness for release, or CoAP root cause is made by this document.
