# Naturally Ordered Long Collections

Naturally Ordered Long Collections provides Java collection implementations for ordered `long` values using sparse bitset windows.

The library is designed for cases where you need to store, scan, queue, or prioritise large numeric identifiers while keeping memory usage predictable and iteration naturally sorted. Instead of storing each `long` as an individual boxed object, values are grouped into fixed-size bitset windows. Each window represents a contiguous range of long values, and each bit represents whether a value in that range is present.

This makes the collections useful for high-throughput systems where identifiers are naturally numeric, sparse, and ordered, such as message sequence numbers, retained offsets, pending acknowledgements, priority work queues, audit ranges, or protocol state tracking.

## Download

[Naturally Ordered Long Collections on Maven Central](https://central.sonatype.com/search?smo=true&q=mapsmessaging)

## Maven setup

Add the dependency to your Maven `pom.xml`:

```xml
<!-- Naturally Ordered Long Collections -->
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>Naturally_Ordered_Long_Collections</artifactId>
  <version>1.1.8</version>
</dependency>
```

## What this package provides

The package provides collection-style abstractions backed by bitsets:

* naturally ordered long collections
* naturally ordered long queues
* naturally ordered long lists
* priority collections and priority queues
* sparse offset-aware bitsets
* in-memory, byte-buffer-backed, file-backed, managed, and hybrid bitset factories

The goal is to provide collection semantics without paying the usual cost of storing large numbers of boxed `Long` instances.

## Why sparse bitsets?

A normal Java collection such as `TreeSet<Long>` stores each value as a separate object. That gives ordered behaviour, but it has significant memory and object-allocation overhead.

This library instead groups values into fixed-size windows.

For example, with a window size of `8192`, the following values:

```text
100
101
8192
8193
5000000
```

can be stored as a small number of bitset windows:

```text
Window 0       -> values 0 to 8191
Window 8192    -> values 8192 to 16383
Window 4997120 -> values 4997120 to 5005311
```

Inside each window, the bit position represents the offset from the window start.

So value `8193` in the `8192` window is stored at bit position `1`.

This gives:

* compact storage for dense local ranges
* sparse storage for widely separated ranges
* naturally ordered iteration
* fast set, clear, contains, and next-value operations
* efficient queue and priority queue implementations over long identifiers

## Core model

The key abstraction is an offset-aware bitset.

An `OffsetBitSet` wraps a raw bitset and gives it a logical start position:

```text
start = 8192
bit 0  -> long value 8192
bit 1  -> long value 8193
bit 63 -> long value 8255
```

The raw bitset only knows about bit indexes. `OffsetBitSet` maps those indexes back to real long values.

This allows the higher-level collections to work with absolute `long` values while storing the data in compact fixed-size windows.

## Collection types

### NaturalOrderedCollection

`NaturalOrderedCollection` stores unique long values and iterates them in natural ascending order.

It is suited to cases where you need set-like behaviour:

* add a long value
* remove a long value
* check whether a value exists
* iterate in sorted order
* retain or remove groups of values
* scan through sparse numeric ranges efficiently

Internally, the collection stores bitset windows in a sorted map keyed by the window start. Iteration walks the windows in ascending order and then walks the set bits inside each window.

### NaturalOrderedLongQueue

`NaturalOrderedLongQueue` provides queue-like access over naturally ordered long values.

Unlike a typical FIFO queue, ordering is based on the numeric value, not insertion order.

This is useful when the next item to process is the lowest available sequence number, offset, or identifier.

Typical operations include:

* `offer(long value)`
* `peek()`
* `poll()`
* `remove(long value)`
* ordered iteration

The queue keeps the final active bitset window available for reuse where appropriate, avoiding unnecessary churn when values are repeatedly added and removed in nearby ranges.

### NaturalOrderedLongList

`NaturalOrderedLongList` provides list-style traversal over ordered long values.

It supports forward and reverse iteration using list iterators, while still using the sparse bitset storage model underneath.

This is useful when you need ordered traversal over a long-valued data set but do not want the object overhead of a normal list of boxed `Long` values.

### PriorityCollection

`PriorityCollection<T>` stores values associated with integer priorities.

A value exists only once logically. Re-adding the same value at the same priority is not a change. Re-adding the same value at a different priority moves it to the new priority.

This gives stable priority grouping without duplicate logical entries.

### PriorityQueue

`PriorityQueue<T>` builds queue semantics on top of the priority collection.

It supports retrieving and removing values by priority order. Values with the same priority are drained in insertion order.

Typical usage includes task scheduling, work dispatch, retry queues, and priority-based protocol operations.

## Bitset implementations

The package includes several bitset implementations and factories so the same collection logic can use different storage strategies.

### BitSetImpl

`BitSetImpl` is the standard in-memory bitset implementation used by the managed factory and most simple cases.

It supports:

* setting bits
* clearing bits
* flipping individual bits
* flipping ranges
* scanning for next or previous set bits
* scanning for next or previous clear bits
* cardinality
* iteration over set bits
* bitwise operations such as `and`, `or`, `xor`, and `andNot`

### ByteBufferBackedBitMap

`ByteBufferBackedBitMap` stores bitset data in a `ByteBuffer`.

This is useful when the backing storage may be direct memory, memory mapped storage, or another byte-buffer-based representation.

It is used by file-backed bitsets and supports the same logical bit operations as the in-memory implementation.

## Bitset factories

Factories are responsible for creating and managing `OffsetBitSet` instances.

This matters because different use cases have different storage and lifecycle requirements.

### ManagedBitSetFactoryImpl

Creates and tracks in-memory `OffsetBitSet` instances.

It maintains mappings from unique IDs to active bitsets, allowing callers to query which bitsets belong to a given unique ID.

It is suitable for normal in-memory use.

### ByteBufferBitSetFactoryImpl

Creates byte-buffer-backed bitsets without maintaining ownership state.

This factory is intentionally lightweight and stateless. It creates independent bitsets and does not track unique IDs.

It is suitable for cases where the caller owns the lifecycle.

### FileBitSetFactoryImpl

Creates bitsets backed by a file.

Each file record stores:

```text
uniqueId
offset/start
bitset data
```

This allows active bitsets to be persisted and later restored by reopening the file.

Released records are moved to a free list and may be reused for future bitsets. When reused, the record is reset with the new start and unique ID, and previous bits are cleared.

Important lifecycle behaviour:

* `release(bitset)` clears the bitset and moves the record to the free list
* `close(bitset)` is intentionally a no-op
* `close()` closes the factory and preserves the file if used records remain
* `close()` deletes the file if no used records remain
* `delete()` forcefully closes and removes the backing file
* `cleanupIfPossible(minSize)` deletes the backing file only when no used records exist and the file is larger than the configured minimum size

This factory is useful when bitset state needs to survive process restarts or when large sparse structures should be backed by disk.

### HybridBitSetFactoryImpl

Uses memory-backed bitsets initially and migrates to file-backed bitsets when a threshold is reached.

This is useful when most unique IDs have only a small number of active windows, but some IDs grow large enough that file-backed storage is preferable.

The hybrid factory keeps delegating wrappers around bitsets so callers can continue using the same logical object while the underlying storage changes.

### DelegatingOffsetBitSet

`DelegatingOffsetBitSet` wraps another `OffsetBitSet` and delegates operations to the current backing bitset.

It is primarily used by the hybrid factory during migration from memory-backed storage to file-backed storage.

The wrapper remains the logical object, while the underlying delegate and factory can be swapped.

This allows the collection to keep stable references while changing storage strategy underneath.

### FileOffsetBitSet

`FileOffsetBitSet` is an `OffsetBitSet` with file-position metadata.

The file position is used by `FileBitSetFactoryImpl` to update the correct record when the bitset is released, reused, or reloaded.

## Ordering behaviour

The collections are naturally ordered by numeric long value.

That means iteration over inserted values such as:

```text
100
5
20
1000
```

will produce:

```text
5
20
100
1000
```

This is different from insertion-order collections.

The ordering comes from two layers:

1. bitset windows are stored by their start offset
2. set bits inside each window are scanned in ascending bit order

## Example usage

### Ordered long collection

```java
NaturalOrderedCollection collection = new NaturalOrderedCollection();

collection.add(100L);
collection.add(5L);
collection.add(20L);

for (Long value : collection) {
  System.out.println(value);
}
```

Output:

```text
5
20
100
```

### Ordered queue

```java
NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue();

queue.offer(42L);
queue.offer(7L);
queue.offer(100L);

System.out.println(queue.peek()); // 7
System.out.println(queue.poll()); // 7
System.out.println(queue.poll()); // 42
```

### Priority queue

```java
PriorityQueue<String> queue = new PriorityQueue<>();

queue.add("normal", 10);
queue.add("urgent", 1);
queue.add("low", 100);

System.out.println(queue.poll()); // urgent
System.out.println(queue.poll()); // normal
System.out.println(queue.poll()); // low
```

### File-backed bitsets

```java
FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl("bitsets.dat", 8192);

OffsetBitSet bitset = factory.open(1L, 0L);
bitset.set(42L);

factory.close();
```

Reopening the same file restores used bitsets:

```java
FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl("bitsets.dat", 8192);

List<OffsetBitSet> bitsets = factory.get(1L);
OffsetBitSet restored = bitsets.get(0);

System.out.println(restored.isSet(42L)); // true

factory.close();
```

## When to use this library

Use this package when:

* values are numeric `long` identifiers
* natural numeric ordering matters
* values may be sparse across a large range
* nearby values may appear in dense clusters
* object allocation overhead matters
* predictable iteration order matters
* queue or priority queue operations need to work over numeric identifiers

Good examples include:

* message sequence numbers
* acknowledgement tracking
* retained offsets
* delivery queues
* protocol windows
* pending task identifiers
* sparse state indexes
* priority work scheduling

## When not to use this library

This package is probably not the right fit when:

* values are not numeric
* insertion order matters more than numeric order
* the data set is tiny
* values are random and never cluster
* normal Java collections are already simple and fast enough
* the extra lifecycle rules of file-backed storage are not worth it

A `TreeSet<Long>` or `LongStream` may be simpler for small or short-lived data.

## Lifecycle notes

For in-memory bitsets, normal Java garbage collection is usually enough.

For file-backed bitsets, lifecycle matters:

```java
FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl("bitsets.dat", 8192);
try {
  OffsetBitSet bitset = factory.open(1L, 0L);
  bitset.set(42L);
} finally {
  factory.close();
}
```

Always close file-backed factories when finished.

On Windows, memory-mapped files can remain locked if the factory is not closed correctly. Use `try/finally` or equivalent resource management around file-backed factories.

## Testing and reliability

The project includes tests covering:

* bitset boundary behaviour
* set, clear, flip, and range flip operations
* next and previous set/clear bit scanning
* iterator and list iterator behaviour
* offset mapping from absolute long values to bit positions
* managed factory lifecycle
* byte-buffer-backed bitsets
* file-backed factory persistence and cleanup
* free-list reuse
* hybrid memory-to-file migration
* priority collection uniqueness and movement between priorities
* naturally ordered queue and list behaviour

These tests are important because many failure modes in sparse bitset collections are subtle: off-by-one boundaries, sentinel `-1` handling, previous-bit scanning, file-backed cleanup, and equality/hash behaviour can all fail quietly if not pinned down.

## SonarCloud Scan

[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-white.svg)](https://sonarcloud.io/summary/new_code?id=Naturally_Ordered_Long_Collections)

## License

This project is licensed under the Apache License 2.0 with the Commons Clause. See the project license for full terms.
 