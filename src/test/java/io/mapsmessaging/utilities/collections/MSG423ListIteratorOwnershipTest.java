/*
 *
 * Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 * Licensed under the Apache License, Version 2.0 with the Commons Clause
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package io.mapsmessaging.utilities.collections;

import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MSG423ListIteratorOwnershipTest {

  @Test
  void insertionAfterCachedEmptyWindowKeepsCursorInNewWindow() {
    NaturalOrderedLongList list = new NaturalOrderedLongList(1L, new BitSetFactoryImpl(64));
    list.add(1L);
    list.remove(1L);

    ListIterator<Long> iterator = list.listIterator();
    iterator.add(64L);

    Assertions.assertEquals(List.of(64L), List.copyOf(list));
    Assertions.assertFalse(iterator.hasNext());
    Assertions.assertTrue(iterator.hasPrevious());
    Assertions.assertEquals(64L, iterator.previous());
    Assertions.assertEquals(64L, iterator.next());
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  void insertionInMiddlePreservesForwardAndReverseCursor() {
    NaturalOrderedLongList list = new NaturalOrderedLongList(1L, new BitSetFactoryImpl(64));
    list.addAll(List.of(1L, 64L));
    ListIterator<Long> iterator = list.listIterator();

    Assertions.assertEquals(1L, iterator.next());
    iterator.add(63L);
    Assertions.assertEquals(63L, iterator.previous());
    Assertions.assertEquals(63L, iterator.next());
    Assertions.assertEquals(64L, iterator.next());
    Assertions.assertEquals(64L, iterator.previous());
    Assertions.assertEquals(63L, iterator.previous());
    Assertions.assertEquals(1L, iterator.previous());
    Assertions.assertFalse(iterator.hasPrevious());
    Assertions.assertEquals(List.of(1L, 63L, 64L), List.copyOf(list));
  }

  @Test
  void releasedFileWindowCannotLeakAnotherOwnersIds(@TempDir Path tempDir) throws IOException {
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(tempDir.resolve("list-ownership.bin").toString(), 64)) {
      NaturalOrderedLongList first = new NaturalOrderedLongList(11L, factory);
      NaturalOrderedLongList second = new NaturalOrderedLongList(22L, factory);
      first.addAll(List.of(1L, 64L));
      OffsetBitSet oldRecord = first.tree.get(0L);
      ListIterator<Long> iterator = first.listIterator();

      Assertions.assertEquals(1L, iterator.next());
      iterator.remove();
      Assertions.assertTrue(second.add(130L));
      Assertions.assertSame(oldRecord, second.tree.get(128L), "Test must exercise actual record reuse");
      Assertions.assertEquals(64L, iterator.next());
      Assertions.assertFalse(iterator.hasNext());
      Assertions.assertEquals(64L, iterator.previous());
      Assertions.assertFalse(iterator.hasPrevious());
      Assertions.assertEquals(List.of(64L), List.copyOf(first));
      Assertions.assertEquals(List.of(130L), List.copyOf(second));
    }
  }

  @Test
  void releasedFutureWindowIsSkippedInBothDirections(@TempDir Path tempDir) throws IOException {
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(tempDir.resolve("list-future.bin").toString(), 64)) {
      NaturalOrderedLongList first = new NaturalOrderedLongList(11L, factory);
      NaturalOrderedLongList second = new NaturalOrderedLongList(22L, factory);
      first.addAll(List.of(-1L, 64L, 128L));
      OffsetBitSet oldRecord = first.tree.get(64L);
      ListIterator<Long> iterator = first.listIterator();
      Assertions.assertTrue(first.remove(64L));
      Assertions.assertTrue(second.add(194L));
      Assertions.assertSame(oldRecord, second.tree.get(192L));

      Assertions.assertEquals(-1L, iterator.next());
      Assertions.assertEquals(128L, iterator.next());
      Assertions.assertFalse(iterator.hasNext());
      Assertions.assertEquals(128L, iterator.previous());
      Assertions.assertEquals(-1L, iterator.previous());
      Assertions.assertFalse(iterator.hasPrevious());
    }
  }

  @Test
  void signedBoundariesDoNotOverflowForwardOrReverse() {
    NaturalOrderedLongList list = new NaturalOrderedLongList(1L, new BitSetFactoryImpl(64));
    List<Long> values = List.of(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE);
    list.addAll(values);
    ListIterator<Long> iterator = list.listIterator();

    for (long value : values) {
      Assertions.assertEquals(value, iterator.next());
    }
    Assertions.assertFalse(iterator.hasNext());
    for (int i = values.size() - 1; i >= 0; i--) {
      Assertions.assertEquals(values.get(i), iterator.previous());
    }
    Assertions.assertFalse(iterator.hasPrevious());
    Assertions.assertEquals(Long.MIN_VALUE, iterator.next());
  }

  @Test
  void iteratorRemoveKeepsNextAndPreviousPositions() {
    NaturalOrderedLongList list = new NaturalOrderedLongList(1L, new BitSetFactoryImpl(64));
    list.addAll(List.of(1L, 63L, 64L, 128L));
    ListIterator<Long> iterator = list.listIterator();

    Assertions.assertEquals(1L, iterator.next());
    iterator.remove();
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
    Assertions.assertEquals(63L, iterator.next());
    Assertions.assertEquals(64L, iterator.next());
    Assertions.assertEquals(64L, iterator.previous());
    iterator.remove();
    Assertions.assertEquals(128L, iterator.next());
    Assertions.assertEquals(128L, iterator.previous());
    Assertions.assertEquals(63L, iterator.previous());
    Assertions.assertFalse(iterator.hasPrevious());
    Assertions.assertEquals(List.of(63L, 128L), List.copyOf(list));
  }
}
