# Full Signed Long Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make naturally ordered collections and persistent IDs support the complete signed Java `long` domain without reserving `-1`.

**Architecture:** Preserve sparse fixed-size bitset windows and local `int` indexes. Introduce signed-safe global window mapping, unambiguous absolute-search results, explicit persistence allocation state, and uniform signed sharding.

**Tech Stack:** Java, Maven, JUnit Jupiter, heap/direct/file-backed bitsets.

**Spec:** `docs/superpowers/specs/2026-09-16-full-signed-long-domain-design.md`

## Global Constraints

- Jira: MSG-307.
- Maven version becomes `2.0.0`.
- `-1L` is a valid value and valid persistent unique ID.
- Raw bitset local indexes remain `int` and may use `-1` only as an internal local-search sentinel.
- Natural order is signed `Long.compare` order.
- Existing legacy positive-domain persistence is migrated or explicitly rejected with a versioned error; preferred path is migration.
- Commits use Conventional Commits and include `Refs: MSG-307`.

---

### Task 1: Add signed window regression tests

**Files:**
- Test: `src/test/java/io/mapsmessaging/utilities/collections/bitset/SignedLongWindowTest.java`
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/BitSetFactory.java`

**Interfaces:**
- Produces: signed-safe `long getStartIndex(long id)` and a bounded local-index helper used by offset bitsets.

- [ ] Add tests asserting the window containing `-1` starts below zero, zero retains the existing zero window, `Long.MIN_VALUE` maps to a representable first window, and `Long.MAX_VALUE` maps to a representable final window for 4096 and a non-divisor size such as 1000.
- [ ] Run `mvn -Dtest=SignedLongWindowTest test`; verify current truncating division fails the negative cases.
- [ ] Replace truncating window arithmetic with overflow-safe floor semantics and explicit lower-bound truncation.
- [ ] Run the focused test and then `mvn test`.
- [ ] Commit as `fix(collections): support signed long window mapping` with `Refs: MSG-307`.

### Task 2: Make absolute offset operations unambiguous

**Files:**
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/OffsetBitSet.java`
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/DelegatingOffsetBitSet.java`
- Test: `src/test/java/io/mapsmessaging/utilities/collections/bitset/OffsetBitSetSignedDomainTest.java`

**Interfaces:**
- Produces: absolute set/clear/isSet operations that validate before subtraction; absolute search methods that return `OptionalLong`; signed-safe comparison via `Long.compare`.

- [ ] Add tests storing and searching `Long.MIN_VALUE`, `-1`, `0`, and `Long.MAX_VALUE` in suitable windows, including compare ordering between extrema and rejection of values outside a window.
- [ ] Run the focused test and verify failures from sentinel collision/overflow.
- [ ] Introduce overflow-safe local index calculation and change absolute search results to `OptionalLong` while retaining raw local `BitSet` sentinel semantics.
- [ ] Replace subtraction-based comparison with `Long.compare` and remove dependence on an unrepresentable exclusive upper end.
- [ ] Run focused and full tests.
- [ ] Commit as `fix(bitset): harden absolute long offsets` with `Refs: MSG-307`.

### Task 3: Support `-1L` through collection and queue APIs

**Files:**
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/NaturalOrderedCollection.java`
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/NaturalOrderedLongQueue.java`
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/NaturalOrderedLongList.java` only if iterator adaptation is required.
- Test: `src/test/java/io/mapsmessaging/utilities/collections/SignedLongCollectionTest.java`
- Test: `src/test/java/io/mapsmessaging/utilities/collections/SignedLongQueueTest.java`

**Interfaces:**
- Consumes: signed-safe factory window mapping and `OptionalLong` absolute search.
- Produces: standard `Queue<Long>` empty semantics with `-1L` treated as data.

- [ ] Add TreeSet-oracle tests with values `{Long.MIN_VALUE, Long.MIN_VALUE+1, -8193, -8192, -8191, -2, -1, 0, 1, 2, 8191, 8192, 8193, Long.MAX_VALUE-1, Long.MAX_VALUE}` inserted out of order.
- [ ] Add queue tests proving `peek/poll/remove/element` return `-1L` normally and empty state uses `null` or `NoSuchElementException` per `Queue` contract.
- [ ] Run focused tests and verify current code fails.
- [ ] Route collection lookup through the signed-safe factory helper and consume unambiguous absolute searches.
- [ ] Run focused and full tests.
- [ ] Commit as `fix(collections): support complete signed value domain` with `Refs: MSG-307`.

### Task 4: Version persistence and remove free-ID sentinel

**Files:**
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/FileBitSetFactoryImpl.java`
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/FileOffsetBitSet.java`
- Test: `src/test/java/io/mapsmessaging/utilities/collections/bitset/FileBitSetSignedPersistenceTest.java`

**Interfaces:**
- Produces: explicit free-record list/state API; v2 file format with allocation state independent of `uniqueId`; migration from legacy records.

- [ ] Add tests that persist/reload `uniqueId=-1`, another negative ID, and values spanning negative/positive windows.
- [ ] Add a test that writes a legacy-format positive-domain file and verifies reopening migrates it without data loss.
- [ ] Run focused tests and verify current sentinel format fails.
- [ ] Add file magic/version/window-size header and record allocation state separate from ID and offset.
- [ ] Replace `get(-1)` free-record access with an explicit factory method/list semantic such as `getFreeBitSets()` used only for lifecycle diagnostics/tests.
- [ ] Implement one-time legacy migration where legacy `uniqueId==-1` means free and all other IDs are preserved.
- [ ] Run focused and full tests.
- [ ] Commit as `feat(persistence)!: separate allocation state from long IDs` with footer `BREAKING CHANGE: file-backed bitset persistence now uses a versioned allocation-state format.` and `Refs: MSG-307`.

### Task 5: Remove negative-ID special casing from shared and hybrid factories

**Files:**
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/SharedFileBitSetFactoryImpl.java`
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/ConcurrentSharedFileBitSetFactoryImpl.java` if signatures change.
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/HybridBitSetFactoryImpl.java` only for new factory interfaces.
- Test: `src/test/java/io/mapsmessaging/utilities/collections/bitset/SharedSignedIdTest.java`

**Interfaces:**
- Consumes: explicit free-record API.
- Produces: all signed IDs routed through `Math.floorMod(uniqueId, shardCount)`.

- [ ] Add persistence/reload tests for `Long.MIN_VALUE`, `-2`, `-1`, `0`, `1`, and `Long.MAX_VALUE` IDs over multiple shards.
- [ ] Run focused tests and verify negative shared IDs fail under current `uniqueId < 0` branch.
- [ ] Remove the negative-ID branch and aggregate free records only through the explicit free-record API.
- [ ] Run focused and full tests.
- [ ] Commit as `fix(sharding): support all signed unique IDs` with `Refs: MSG-307`.

### Task 6: Enforce backend boundary parity

**Files:**
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/BitSetImpl.java`
- Modify: `src/main/java/io/mapsmessaging/utilities/collections/bitset/ByteBufferBackedBitMap.java`
- Modify constructors/factories if window-size validation is needed.
- Test: `src/test/java/io/mapsmessaging/utilities/collections/bitset/BitSetBoundaryParityTest.java`

**Interfaces:**
- Produces: identical `[0, capacity)` local-index semantics for heap and mapped bitsets.

- [ ] Add parameterized tests for first bit, final legal bit, `capacity`, `-1`, searches at edges, and supported non-64-aligned sizes.
- [ ] Run focused tests and verify existing heap/mapped boundary disagreement.
- [ ] Make both backends use the same exclusive upper bound and either correctly represent remainder bits or reject unsupported sizes consistently at construction.
- [ ] Run focused and full tests.
- [ ] Commit as `fix(bitset): align backend boundary semantics` with `Refs: MSG-307`.

### Task 7: Add broad signed-domain integration coverage

**Files:**
- Test: `src/test/java/io/mapsmessaging/utilities/collections/SignedLongDomainIntegrationTest.java`
- Extend existing queue/list/factory tests where lifecycle coverage belongs there.

**Interfaces:**
- Verifies: heap, direct buffer, managed, file, ephemeral, shared, concurrent shared, hybrid/delegating behaviour.

- [ ] Build a deterministic signed boundary corpus plus seeded random long samples and compare collection iteration/removal against `TreeSet<Long>`.
- [ ] Exercise close/reopen for file-capable factories and forward/reverse iteration for list semantics.
- [ ] Exercise set operations `addAll`, `containsAll`, `removeAll`, and `retainAll` across zero.
- [ ] Run the new integration suite, then the complete Maven test suite.
- [ ] Commit as `test(collections): cover complete signed long domain` with `Refs: MSG-307`.

### Task 8: Apply major version and compatibility documentation

**Files:**
- Modify: `pom.xml`
- Modify: `README.md`

**Interfaces:**
- Produces artifact version `2.0.0` and documents breaking sentinel/persistence changes.

- [ ] Change Maven version from `1.2.2` to `2.0.0`.
- [ ] Document full signed-domain support, removal of `-1` sentinel semantics, and persistence migration.
- [ ] Run `mvn clean test` and inspect test reports for zero failures/errors.
- [ ] Commit as `build!: bump naturally ordered collections to 2.0.0` with `BREAKING CHANGE: full signed long support removes -1 sentinel semantics and versions persistence.` and `Refs: MSG-307`.
