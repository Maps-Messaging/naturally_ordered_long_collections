# Naturally Ordered Long Collections

Implements Java collections over sparse bitsets, providing naturally ordered collections, lists, queues and priority collections.

## Version 2.0 signed `long` support

Version 2.0 supports values and persistent unique IDs across the complete signed Java `long` domain, from `Long.MIN_VALUE` through `Long.MAX_VALUE`.

Important 2.0 changes:

- `-1L` is an ordinary collection value and persistent unique ID. It is no longer reserved as a global sentinel.
- Absolute `OffsetBitSet` search methods return `null` when no matching absolute value exists. The underlying local `BitSet` API remains `int` indexed and continues to use `-1` internally where it cannot collide with a legal local index.
- File-backed bitsets use a versioned persistence format with explicit allocated/free record state rather than encoding free state as `uniqueId == -1`.
- Existing legacy positive-domain files are migrated to the versioned format when opened.
- Shared-file sharding uses `Math.floorMod` for every signed unique ID, including negative IDs and `Long.MIN_VALUE`.
- Window and offset arithmetic is overflow-safe at both signed extrema.

The collection remains sparse: supporting the complete signed address domain does not allocate storage for the domain itself. Storage is allocated only for windows containing values.

## Maven setup

All MapsMessaging libraries are hosted on [Maven Central](https://central.sonatype.com/search?smo=true&q=mapsmessaging).

```xml
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>naturally_ordered_long_collections</artifactId>
  <version>2.0.0</version>
</dependency>
```

[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-white.svg)](https://sonarcloud.io/summary/new_code?id=Naturally_Ordered_Long_Collections)
