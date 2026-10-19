# Signed `long` domain and migration guide (2.0.0)

This guide describes the breaking signed-`long` changes introduced in
`io.mapsmessaging:naturally_ordered_long_collections:2.0.0` (MSG-307).
It applies to MAPS Server and to any application using its queues, collections,
offset bitsets, or persistent bitset factories.

**Primary rule:** the complete Java `long` domain is now valid application
data and valid persistent `uniqueId` space, including `-1L`,
`Long.MIN_VALUE`, `0L`, and `Long.MAX_VALUE`. Consumers must not assume
that a *global `long` ID or stored value* of `-1L` means empty, free, absent,
or invalid.

This is a **major-version behavioural and persistence compatibility change**.
Merely changing a Maven dependency version does not migrate downstream APIs
that still use `-1L` as a not-found result.

## What changed in the library

| Area | Previous assumption | 2.0.0 behaviour |
| --- | --- | --- |
| Stored values | Effectively zero-or-positive; `-1L` could mean not found | Any signed `long` can be stored and ordered |
| Persistent `uniqueId` | `-1L` identified a free record | `-1L` is an ordinary allocated ID |
| Queue empty result | Callers sometimes expected a numeric sentinel | `poll()` / `peek()` return `null`; `remove()` / `element()` throw `NoSuchElementException` |
| Absolute `OffsetBitSet` search | `-1L` used as not found | Returns boxed `Long`; `null` means no result, `-1L` can be a match |
| Raw `BitSet` search | `-1` used as no local index | Still returns `int` `-1` for no match **inside a bitset** |
| Window selection | Nonnegative/truncating arithmetic | Signed floor-based selection, including truncated edge windows |
| Sharded persistent IDs | Negative IDs could have special meaning | All IDs route via `Math.floorMod(uniqueId, shardCount)` |
| File persistence | Unversioned records and `uniqueId == -1` free marker | Versioned file header; allocation state stored separately from ID |

**Scope of the sentinel rule:** `-1` is not universally forbidden in every
integer API. For example, the *internal* `BitSet.nextSetBit(int)` can safely
return `-1` because valid local bit indexes are nonnegative integers.
Similarly, an application-specific priority index can have its own documented
not-found convention. The collision happens when a `-1L` sentinel shares
the same domain as a valid collection value or persistent ID.

## Collection and queue callers

The queue now uses the normal Java `Queue<Long>` contract:

```java
Queue<Long> queue = new NaturalOrderedLongQueue();
queue.offer(-1L);

Long first = queue.poll(); // -1L: a real stored value
Long absent = queue.poll(); // null: the queue is empty

if (absent == null) {
  // No value available.
}
```

- `peek()`: returns the smallest signed stored value, or `null` if empty.
- `poll()`: removes and returns the smallest signed stored value, or `null`.
- `element()`: returns the smallest signed stored value without removal;
  throws `NoSuchElementException` if empty.
- `remove()` (no arguments): removes the smallest signed stored value;
  throws `NoSuchElementException` if empty.
- `remove(Object)`: removes that **specific** value; this is not the same
  method as no-argument `remove()`.

**Do not do this:** `if (queue.poll() == -1L) { /* empty */ }`.
That check both confuses a valid `-1L` and can attempt to unbox `null`.
Likewise, avoid `value < 0` as a generic validity test without a separately
defined application-level restriction.

For a service API that currently returns primitive `long` with `-1L` for
“nothing”, migrate the service boundary to an unambiguous result, for example:

```java
OptionalLong nextAvailableId(Queue<Long> queue) {
  Long value = queue.peek();
  return value == null ? OptionalLong.empty() : OptionalLong.of(value);
}
```

An explicitly nullable `Long` or a separate boolean/presence result also
works. This is a downstream application API decision, **not** an automatic
change made by the collection library.

## Absolute search versus local search

`OffsetBitSet` operates with **absolute `long` values**. The following
methods return nullable boxed `Long` results:

- `nextSetBit(long)`
- `nextSetBitAndClear(long)`
- `nextClearBit(long)`
- `previousSetBit(long)`
- `previousClearBit(long)`

When they cannot find a bit within the logical window, they return `null`.
Their result may legitimately be `-1L`; do not compare a search result
against `-1L` to determine presence.

By contrast, `io.mapsmessaging.utilities.collections.bitset.BitSet` uses
**local `int` indexes** in `[0, capacity)`. Its local search methods still
return `-1` when no local bit index exists.

For window arithmetic, use `BitSetFactory.getStartIndex(long)` and
`getWindowLength(long)`. They handle negative values and the
`Long.MIN_VALUE` / `Long.MAX_VALUE` edges. Avoid truncating integer
division, subtracting IDs to compare them, or assuming every window has
the full configured capacity. At `Long.MAX_VALUE`, a mathematical
exclusive end of `Long.MAX_VALUE + 1` cannot be represented by a `long`;
do not infer membership using only `OffsetBitSet.getEnd()`. Prefer
range-checked methods such as `isSet(long)`.

## Persistence and record lifecycle

The 2.0.0 file-backed format separates allocation state from `uniqueId`:

- A file header stores magic, format version (`2`) and window size.
- Each record stores an explicit `FREE` or `ALLOCATED` state, followed
  by signed `uniqueId`, signed window start, and the bitmap payload.
- `FileBitSetFactoryImpl.get(-1L)` now requests **allocated records for
  ID `-1L`**, not the free list.
- Use `getFreeBitSets()` to inspect the explicit free-record list, and
  `getUniqueIds()` to enumerate allocated IDs.
- Shared file-backed factories shard negative and positive IDs uniformly
  using `Math.floorMod(uniqueId, shardCount)`.

Example of `-1L` as *both* an ordinary persistent ID and an ordinary
stored value:

```java
try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filePath, 192)) {
  NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
  queue.offer(-1L);
  queue.offer(0L);
}
```

### Opening legacy files

When a legacy (pre-2.0.0) file is opened, the factory migrates its records
to the new format. In that **legacy-only** layout, `uniqueId == -1L`
meant a free record; those records are not migrated as allocated IDs.
Other legacy allocated IDs are migrated. The new layout can then store
`-1L` without ambiguity.

**Operational precautions for persisted deployments:**

1. Back up existing `.bit`/factory backing files while the producing service
   is stopped, including all shards belonging to the same logical store.
2. Upgrade the library and reopen the files with their original window-size
   and shard configuration; migration is performed when an old-format file
   is opened.
3. Validate restored IDs, signed values, free-record accounting, and any
   queue/message replay that depends on them.
4. Do not assume that older 1.x binaries can reopen the newly written
   format. Plan rollback around the pre-upgrade backup, not around reusing
   files after migration.
5. An incompatible window size, unsupported version or malformed record
   is rejected; investigate the cause instead of deleting the data to
   make startup succeed.

The in-place migration is not an application-level transactional backup.
Treat the persisted file format as a deliberate upgrade boundary.

## MAPS Server: integration review

The server may have independent contracts that still return a primitive
`long` and reserve `-1L`. These calls are **not automatically updated**
by switching its collection dependency to 2.0.0.

The following references were identified in
[`Maps-Messaging/mapsmessaging_server`](https://github.com/Maps-Messaging/mapsmessaging_server),
`development` at the time of this documentation review:

| Server location | Current pattern | Migration consideration |
| --- | --- | --- |
| [`engine/destination/delayed/DelayedBucket.java`](https://github.com/Maps-Messaging/mapsmessaging_server/blob/development/src/main/java/io/mapsmessaging/engine/destination/delayed/DelayedBucket.java) | Converts `delayedMessageState.peek() == null` to primitive `-1L` | Ambiguous if message ID `-1L` is accepted; return a presence-aware result or explicitly enforce a nonnegative ID domain at this API boundary |
| [`engine/destination/delayed/MessageManager.java`](https://github.com/Maps-Messaging/mapsmessaging_server/blob/development/src/main/java/io/mapsmessaging/engine/destination/delayed/MessageManager.java) | `getNext(long)` returns `-1L` for missing/empty buckets | A real queued `-1L` is indistinguishable from no message; inspect all callers before changing the return contract |
| [`engine/destination/subscription/state/BaseMessageStateManager.java`](https://github.com/Maps-Messaging/mapsmessaging_server/blob/development/src/main/java/io/mapsmessaging/engine/destination/subscription/state/BaseMessageStateManager.java) | `nextMessageId()` translates an empty `peek()` into `-1L` | Same ambiguity; downstream consumers must not mistake a real stored `-1L` for empty |
| [`engine/destination/subscription/state/BaseMessageStateManager.java`](https://github.com/Maps-Messaging/mapsmessaging_server/blob/development/src/main/java/io/mapsmessaging/engine/destination/subscription/state/BaseMessageStateManager.java) | Retained-replay namespace and priority restrictions are documented as temporary until signed IDs work | Re-evaluate those **server** workarounds when adopting 2.0.0; do not remove them without confirming namespace and persistence implications |
| [`engine/destination/RetainManager.java`](https://github.com/Maps-Messaging/mapsmessaging_server/blob/development/src/main/java/io/mapsmessaging/engine/destination/RetainManager.java) | Uses `-1L` for “no retain/clear”, and `-2L` internally for uninitialised state | May remain valid only if retain IDs are explicitly restricted to a different domain; it is not a safe generic collection sentinel |

The server's `development/pom.xml` was observed using the property
`maps.nolc.version = 1.2.3-SNAPSHOT`. Update the dependency in the
**server's own change and validation process** when ready for 2.0.0.
No server source or dependency is modified by this guide.

Other known server users of the library include the event-reaper queue,
CoAP option ordering, subscription/delivery queues, delayed scheduling
and MQTT 5 outstanding identifiers. They should be checked for
`== -1`, `!= -1`, `< 0`, `>= 0`, or unboxing of nullable queue/search
results. **Not every such comparison is wrong:** some are valid protocol
limits or priority-index sentinels. Review the actual numeric domain of
each API rather than performing a global search-and-replace.

## Consumer upgrade checklist

- [ ] Identify every location where `-1L` indicates “not found”, “empty”,
      “free”, “uninitialised”, or “invalid” for a stored `long` or ID.
- [ ] Change ambiguous primitive-`long` results to nullable boxed `Long`,
      `OptionalLong`, or a separate explicit presence/status result.
- [ ] Update queue empty handling: `poll/peek -> null`;
      `remove/element -> NoSuchElementException`.
- [ ] Update `OffsetBitSet` callers to handle nullable `Long` search results;
      leave local `int`-bit-index handling separate.
- [ ] Replace `get(-1L)` free-record lookups with `getFreeBitSets()`.
- [ ] Check persisted `uniqueId` generation, shard routing, restart/replay,
      and any caller-side restrictions on negative IDs.
- [ ] Back up and migrate legacy persistent files; test restart and rollback
      using realistic data.
- [ ] Add regression tests for `-1L`, `0L`, both signed extrema, negative
      windows, missing/empty results and mixed sign ordering.
- [ ] Run the dependent project's full regression suite before deployment.

See also the [design specification](superpowers/specs/2026-09-16-full-signed-long-domain-design.md)
for implementation rationale. This document describes the shipped 2.0.0
behaviour rather than the alternatives proposed during design.
