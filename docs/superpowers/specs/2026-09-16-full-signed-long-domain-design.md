# Full Signed Long Domain Support Design

## Goal

Support every Java `long` value and persistent unique ID from `Long.MIN_VALUE` through `Long.MAX_VALUE` without reserving `-1`, while preserving sparse natural ordering, fixed-size local bitsets, sharding, and restart persistence.

## Existing assumptions being removed

The current implementation was written for zero-or-positive values. It uses truncating division for window selection, uses `-1` as an absolute search/queue sentinel, stores `uniqueId == -1` as the persistent free-record marker, and treats negative shared-factory IDs as free-list lookups.

Those conventions are incompatible with the complete signed domain because `-1L` must be an ordinary value and ordinary persistent ID.

## Window model

Keep the sparse-window architecture and `int` local bit indexes. A global `long` is mapped to a window start and then to a bounded local index.

Interior windows remain aligned to multiples of `windowSize` around zero. Negative values use floor semantics rather than Java truncation. The first representable window is truncated at `Long.MIN_VALUE` when its mathematical aligned start would fall below the signed domain. The final window is truncated at `Long.MAX_VALUE` when its mathematical exclusive end would exceed the signed domain.

The mapping helper must never use arithmetic that can overflow before range validation. Local index calculation is performed only after confirming the value belongs to the selected window and must yield `0 <= index < effectiveWindowLength`.

## Absolute search semantics

The raw `BitSet` interface remains locally indexed by `int`; local search methods may continue using `-1` because `-1` is not a valid local bit index.

`OffsetBitSet` must not expose `-1` as an absolute-value not-found marker. Absolute searches use an unambiguous result, preferably `OptionalLong`, so an actual stored value of `-1L` is representable.

`Queue<Long>` semantics remain conventional: `poll()` and `peek()` use `null` for empty, while `remove()` and `element()` throw `NoSuchElementException` when empty. A queued `-1L` is returned normally.

## Ordering and bounds

All global ordering uses `Long.compare`, never subtraction.

Do not derive membership from an exclusive end that would require representing `Long.MAX_VALUE + 1`. Window ownership is expressed as start plus effective length or equivalent overflow-safe checks. Existing `getEnd()` behaviour must be replaced or clarified for the major version so the upper domain is not represented incorrectly.

## Persistence format

Persistent allocation state becomes independent of the ID value.

Introduce a versioned file format containing a file header with magic, format version, and window size. Each record stores an explicit allocation state followed by the full signed `uniqueId`, full signed window start, and bitmap payload. No ID or offset value is reserved.

Existing legacy files have records containing `uniqueId`, offset and bitmap with `uniqueId == -1` meaning free. On opening a legacy file, migrate used records to the new format. Legacy `-1` records are treated as free because legacy APIs did not support `-1` as a persistent ID. Other negative IDs, if present, are preserved as ordinary IDs during migration.

Free records are exposed through explicit factory/list semantics rather than `get(-1)`.

## Sharding

Retain `Math.floorMod(uniqueId, shardCount)`. Remove the special negative-ID branch from shared factories. Every signed ID, including `Long.MIN_VALUE` and `-1L`, is assigned through the same shard computation.

## Bitset backend invariants

Heap and byte-buffer backends must agree that valid local indexes are `[0, capacity)`. Window sizes must produce a representable local capacity consistently across implementations. Tests will cover boundary parity and non-64-aligned sizes; if the mapped implementation cannot safely support arbitrary sizes, constructor validation will make that constraint explicit rather than silently truncating capacity.

## Compatibility and versioning

This is a breaking semantic and persistence change. The Maven artifact version moves from `1.2.2` to `2.0.0`.

Existing Java collection surfaces are preserved where their standard contracts already provide unambiguous empty semantics. Lower-level absolute search and free-list APIs may change because preserving those exact signatures would make the full signed domain mathematically impossible.

## Verification

Use test-first coverage against a trusted `TreeSet<Long>` reference where appropriate. Cover both signed extrema, `-1`, zero crossing, negative and positive window edges, mixed insertion order, forward/reverse iteration, queue operations, all storage backends, all factory types, negative IDs, sharding, legacy-file migration, and close/reopen persistence.