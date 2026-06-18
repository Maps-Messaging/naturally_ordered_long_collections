/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.mapsmessaging.utilities.collections;

import io.mapsmessaging.utilities.collections.bitset.BitSetFactory;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class NaturalOrderedLongQueueTest {

  protected NaturalOrderedLongQueue build(long id, BitSetFactory factory) {
    return new NaturalOrderedLongQueue(id, factory);
  }

  @Test
  void simplePollOfferTest() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(4096);
    NaturalOrderedLongQueue noll = build(0, factory);
    for (long x = 8192; x < 2 * 8192; x++) {
      noll.offer(x);
    }

    for (long x = 0; x < 8192; x++) {
      noll.offer(x);
    }
    int counter = noll.size();
    Assertions.assertNotEquals(counter, 0);
    int start = 0;
    while (!noll.isEmpty()) {
      long ele = noll.element();
      long poll = noll.poll();
      Assertions.assertEquals(ele, poll);
      Assertions.assertEquals(start, poll);
      start++;
    }
  }

  @Test
  void simpleAddRemoveTest() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(4096);
    NaturalOrderedLongQueue noll = build(0, factory);
    for (long x = 8192; x < 2 * 8192; x++) {
      noll.add(x);
    }

    for (long x = 0; x < 8192; x++) {
      noll.offer(x);
    }
    int counter = noll.size();
    Assertions.assertNotEquals(counter, 0);
    int start = 0;
    while (!noll.isEmpty()) {
      Assertions.assertEquals(start, noll.remove());
      start++;
    }
  }

  @Test
  void pollToEmptyAllowsReuseWithoutStaleHead() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());

    noll.offer(2L);
    Assertions.assertEquals(2L, noll.peek());
    Assertions.assertEquals(2L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());

    noll.offer(65536L);
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());

    noll.offer(1L);
    Assertions.assertEquals(1L, noll.peek());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void pollsBoundaryValuesInNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    long[] values = {257L, 0L, 129L, 1L, 128L, 127L, 65L, 64L, 63L, 62L, 255L, 256L, 126L};
    for (long value : values) {
      noll.offer(value);
    }

    long[] expected = {0L, 1L, 62L, 63L, 64L, 65L, 126L, 127L, 128L, 129L, 255L, 256L, 257L};
    Assertions.assertEquals(expected.length, noll.size());

    for (long value : expected) {
      Assertions.assertEquals(value, noll.poll());
    }

    Assertions.assertNull(noll.poll());
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
  }

  @Test
  void sparseChurnMaintainsOrderSizeAndReuse() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(65536L);
    noll.offer(1024L);
    noll.offer(65535L);
    noll.offer(4096L);

    Assertions.assertTrue(noll.remove(4096L));
    Assertions.assertFalse(noll.remove(12345L));
    noll.offer(4096L);

    Assertions.assertEquals(4, noll.size());
    Assertions.assertEquals(1024L, noll.poll());
    Assertions.assertEquals(4096L, noll.poll());
    Assertions.assertEquals(65535L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());

    noll.offer(257L);
    Assertions.assertEquals(257L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeFirstMiddleAndLastPreservesNeighbours() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(0L);
    noll.offer(1L);
    noll.offer(63L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(128L);
    noll.offer(129L);

    Assertions.assertTrue(noll.remove(0L));
    Assertions.assertTrue(noll.remove(64L));
    Assertions.assertTrue(noll.remove(129L));

    Assertions.assertEquals(4, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(63L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertEquals(128L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void clearAfterSparseValuesAllowsReuse() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1024L);
    noll.offer(4096L);
    noll.offer(65535L);
    noll.offer(65536L);

    noll.clear();

    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());

    noll.offer(255L);
    Assertions.assertEquals(255L, noll.peek());
    Assertions.assertEquals(255L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void duplicateOfferDoesNotIncreaseSize() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    Assertions.assertTrue(noll.offer(64L));
    Assertions.assertFalse(noll.offer(64L));

    Assertions.assertEquals(1, noll.size());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertNull(noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeMissingValueDoesNotChangeQueue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(63L);
    noll.offer(64L);
    noll.offer(65L);

    Assertions.assertFalse(noll.remove(128L));
    Assertions.assertEquals(3, noll.size());

    Assertions.assertEquals(63L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeThenAddSameValueWorks() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(256L);
    Assertions.assertTrue(noll.remove(256L));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());

    noll.offer(256L);
    Assertions.assertEquals(256L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllMatchingCollectionRemovesValuesFromMissingWindows() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue retained = build(1, factory);

    noll.offer(1L);
    noll.offer(65536L);

    retained.offer(1L);

    Assertions.assertTrue(noll.retainAll(retained));
    Assertions.assertEquals(1, noll.size());
    Assertions.assertTrue(noll.contains(1L));
    Assertions.assertFalse(noll.contains(65536L));
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllMatchingCollectionRemovesPartialAndMissingWindows() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue retained = build(1, factory);

    noll.offer(1L);
    noll.offer(2L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(65536L);

    retained.offer(2L);
    retained.offer(65L);

    Assertions.assertTrue(noll.retainAll(retained));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertFalse(noll.contains(1L));
    Assertions.assertTrue(noll.contains(2L));
    Assertions.assertFalse(noll.contains(64L));
    Assertions.assertTrue(noll.contains(65L));
    Assertions.assertFalse(noll.contains(65536L));

    Assertions.assertEquals(2L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeAllMatchingCollectionRemovesSparseWindows() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue removed = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(65536L);

    removed.offer(64L);
    removed.offer(65536L);

    Assertions.assertTrue(noll.removeAll(removed));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertTrue(noll.contains(1L));
    Assertions.assertFalse(noll.contains(64L));
    Assertions.assertTrue(noll.contains(65L));
    Assertions.assertFalse(noll.contains(65536L));

    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeAllMatchingCollectionReturnsFalseWhenUnchanged() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue removed = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    removed.offer(2L);
    removed.offer(65L);

    Assertions.assertFalse(noll.removeAll(removed));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }
  @Test
  void removeAllPlainCollectionReturnsFalseWhenUnchanged() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertFalse(noll.removeAll(Arrays.asList(2L, 65L)));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void addAllReturnsFalseWhenAddingOnlyDuplicates() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);

    Assertions.assertFalse(noll.addAll(Arrays.asList(1L, 64L)));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllPlainCollectionReturnsFalseWhenUnchanged() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertFalse(noll.retainAll(Arrays.asList(1L, 64L, 65536L)));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllPlainCollectionReturnsTrueWhenChanged() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.retainAll(Arrays.asList(64L)));
    Assertions.assertEquals(1, noll.size());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllEmptyCollectionClearsQueue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.retainAll(Arrays.asList()));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
    Assertions.assertNull(noll.poll());
  }

  @Test
  void retainAllEmptyCollectionReturnsFalseWhenAlreadyEmpty() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    Assertions.assertFalse(noll.retainAll(Arrays.asList()));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
  }

  @Test
  void removeAllEmptyCollectionReturnsFalse() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);

    Assertions.assertFalse(noll.removeAll(Arrays.asList()));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void addAllEmptyCollectionReturnsFalse() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    Assertions.assertFalse(noll.addAll(Arrays.asList()));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
  }

  @Test
  void removeIfReturnsFalseWhenNothingMatches() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertFalse(noll.removeIf(value -> value > 100000L));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeIfReturnsTrueWhenValuesRemoved() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.removeIf(value -> value >= 64L));
    Assertions.assertEquals(1, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void peekDoesNotRemoveValue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(64L);
    noll.offer(1L);

    Assertions.assertEquals(1L, noll.peek());
    Assertions.assertEquals(2, noll.size());
    Assertions.assertEquals(1L, noll.peek());
    Assertions.assertEquals(2, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void pollOnEmptyReturnsNull() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    Assertions.assertNull(noll.poll());
    Assertions.assertNull(noll.peek());
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
  }

  @Test
  void removeOnEmptyThrowsNoSuchElementException() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    Assertions.assertThrows(java.util.NoSuchElementException.class, noll::remove);
  }

  @Test
  void elementOnEmptyReturnsNullByCurrentContract() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    Assertions.assertNull(noll.element());
  }

  @Test
  void removeReturnsNaturalOrderAndThrowsWhenDrained() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(128L);
    noll.offer(1L);
    noll.offer(64L);

    Assertions.assertEquals(1L, noll.remove());
    Assertions.assertEquals(64L, noll.remove());
    Assertions.assertEquals(128L, noll.remove());
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertThrows(java.util.NoSuchElementException.class, noll::remove);
  }

  @Test
  void typedToArrayCopiesValuesInNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(65536L);
    noll.offer(1L);
    noll.offer(64L);
    noll.offer(63L);

    Long[] values = new Long[4];
    Long[] response = noll.toArray(values);

    Assertions.assertSame(values, response);
    Assertions.assertArrayEquals(new Long[] {1L, 63L, 64L, 65536L}, response);
  }

  @Test
  void typedToArrayWithLargerArrayLeavesTrailingEntriesUnchanged() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(64L);
    noll.offer(1L);

    Long[] values = {0L, 0L, 99L, 100L};
    Long[] response = noll.toArray(values);

    Assertions.assertSame(values, response);
    Assertions.assertArrayEquals(new Long[] {1L, 64L, 99L, 100L}, response);
  }

  @Test
  void iteratorRemoveRemovesCurrentValue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65L);

    java.util.Iterator<Long> iterator = noll.iterator();
    Assertions.assertTrue(iterator.hasNext());
    Assertions.assertEquals(1L, iterator.next());
    iterator.remove();

    Assertions.assertEquals(2, noll.size());
    Assertions.assertFalse(noll.contains(1L));
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void addAllMatchingCollectionReturnsFalseWhenOnlyDuplicates() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue values = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    values.offer(1L);
    values.offer(64L);
    values.offer(65536L);

    Assertions.assertFalse(noll.addAll(values));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void addAllMatchingCollectionReturnsTrueWhenAddingSparseValues() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue values = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);

    values.offer(1L);
    values.offer(65L);
    values.offer(65536L);

    Assertions.assertTrue(noll.addAll(values));
    Assertions.assertEquals(4, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeAllMatchingCollectionReturnsTrueWhenChanged() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue removed = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(65536L);

    removed.offer(64L);
    removed.offer(65536L);

    Assertions.assertTrue(noll.removeAll(removed));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeAllMatchingCollectionWithEmptyCollectionReturnsFalse() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue removed = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertFalse(noll.removeAll(removed));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllMatchingCollectionWithEmptyCollectionClearsQueue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue retained = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.retainAll(retained));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
    Assertions.assertNull(noll.poll());
  }

  @Test
  void retainAllMatchingCollectionWithEmptyCollectionReturnsFalseWhenAlreadyEmpty() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue retained = build(1, factory);

    Assertions.assertFalse(noll.retainAll(retained));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
  }

  @Test
  void fileBackedQueuePersistsBoundaryAndSparseValuesInNaturalOrder() throws IOException {
    final String filename = "./queuePersistenceBoundarySparse.bit";
    final int windowSize = 64;
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filename, windowSize);

    try {
      NaturalOrderedLongQueue noll = build(42, factory);

      noll.offer(65536L);
      noll.offer(1L);
      noll.offer(64L);
      noll.offer(63L);
      noll.offer(4096L);

      factory.close();

      factory = new FileBitSetFactoryImpl(filename, windowSize);
      noll = build(42, factory);

      Assertions.assertEquals(5, noll.size());
      Assertions.assertEquals(1L, noll.poll());
      Assertions.assertEquals(63L, noll.poll());
      Assertions.assertEquals(64L, noll.poll());
      Assertions.assertEquals(4096L, noll.poll());
      Assertions.assertEquals(65536L, noll.poll());
      Assertions.assertTrue(noll.isEmpty());
    } finally {
      factory.close();
      Files.deleteIfExists(Paths.get(filename));
    }
  }

  @Test
  void fileBackedQueuePersistsRemovedValuesAsRemoved() throws IOException {
    final String filename = "./queuePersistenceRemovedValues.bit";
    final int windowSize = 64;
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filename, windowSize);

    try {
      NaturalOrderedLongQueue noll = build(42, factory);

      noll.offer(1L);
      noll.offer(64L);
      noll.offer(65L);
      noll.offer(65536L);

      Assertions.assertTrue(noll.remove(64L));
      Assertions.assertTrue(noll.remove(65536L));

      factory.close();

      factory = new FileBitSetFactoryImpl(filename, windowSize);
      noll = build(42, factory);

      Assertions.assertEquals(2, noll.size());
      Assertions.assertEquals(1L, noll.poll());
      Assertions.assertEquals(65L, noll.poll());
      Assertions.assertTrue(noll.isEmpty());
    } finally {
      factory.close();
      Files.deleteIfExists(Paths.get(filename));
    }
  }

  @Test
  void fileBackedQueueClearPersistsEmptyState() throws IOException {
    final String filename = "./queueClearPersistsEmpty.bit";
    final int windowSize = 64;
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filename, windowSize);

    try {
      NaturalOrderedLongQueue noll = build(42, factory);

      noll.offer(1L);
      noll.offer(64L);
      noll.offer(65536L);
      noll.clear();

      factory.close();

      factory = new FileBitSetFactoryImpl(filename, windowSize);
      noll = build(42, factory);

      Assertions.assertTrue(noll.isEmpty());
      Assertions.assertEquals(0, noll.size());
      Assertions.assertNull(noll.poll());

      noll.offer(257L);
      Assertions.assertEquals(257L, noll.poll());
      Assertions.assertTrue(noll.isEmpty());
    } finally {
      factory.close();
      Files.deleteIfExists(Paths.get(filename));
    }
  }

  @Test
  void fileBackedQueuesRemainIsolatedByUniqueId() throws IOException {
    final String filename = "./queueUniqueIdIsolation.bit";
    final int windowSize = 64;
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filename, windowSize);

    try {
      NaturalOrderedLongQueue first = build(1, factory);
      NaturalOrderedLongQueue second = build(2, factory);

      first.offer(64L);
      first.offer(65536L);

      second.offer(1L);
      second.offer(4096L);

      factory.close();

      factory = new FileBitSetFactoryImpl(filename, windowSize);
      first = build(1, factory);
      second = build(2, factory);

      Assertions.assertEquals(2, first.size());
      Assertions.assertEquals(64L, first.poll());
      Assertions.assertEquals(65536L, first.poll());
      Assertions.assertTrue(first.isEmpty());

      Assertions.assertEquals(2, second.size());
      Assertions.assertEquals(1L, second.poll());
      Assertions.assertEquals(4096L, second.poll());
      Assertions.assertTrue(second.isEmpty());
    } finally {
      factory.close();
      Files.deleteIfExists(Paths.get(filename));
    }
  }

  @Test
  void fileBackedQueueCanReloadAfterPollToEmptyAndReuse() throws IOException {
    final String filename = "./queuePollToEmptyReloadReuse.bit";
    final int windowSize = 64;
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filename, windowSize);

    try {
      NaturalOrderedLongQueue noll = build(42, factory);

      noll.offer(1L);
      Assertions.assertEquals(1L, noll.poll());
      Assertions.assertTrue(noll.isEmpty());

      noll.offer(65536L);

      factory.close();

      factory = new FileBitSetFactoryImpl(filename, windowSize);
      noll = build(42, factory);

      Assertions.assertEquals(1, noll.size());
      Assertions.assertEquals(65536L, noll.poll());
      Assertions.assertTrue(noll.isEmpty());
    } finally {
      factory.close();
      Files.deleteIfExists(Paths.get(filename));
    }
  }

  @Test
  void closeThenCreateNewQueueInstanceAllowsReuse() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.close();

    NaturalOrderedLongQueue reused = build(0, factory);
    reused.offer(65L);

    Assertions.assertEquals(65L, reused.poll());
    Assertions.assertTrue(reused.isEmpty());
  }

  @Test
  void iteratorSeesValuesAddedToExistingWindowsOnly() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);

    java.util.Iterator<Long> iterator = noll.iterator();

    noll.offer(65L);
    noll.offer(65536L);

    List<Long> values = new ArrayList<>();
    while (iterator.hasNext()) {
      values.add(iterator.next());
    }

    Assertions.assertEquals(Arrays.asList(1L, 64L, 65L), values);
    Assertions.assertEquals(4, noll.size());
    Assertions.assertTrue(noll.contains(65536L));
  }

  @Test
  void iteratorCreatedBeforeNewWindowDoesNotLoseQueueContents() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);

    java.util.Iterator<Long> iterator = noll.iterator();

    noll.offer(65L);
    noll.offer(65536L);

    while (iterator.hasNext()) {
      iterator.next();
    }

    Assertions.assertEquals(4, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeIfRemovesBoundarySubsetOnly() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(62L);
    noll.offer(63L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(126L);
    noll.offer(127L);
    noll.offer(128L);
    noll.offer(129L);

    Assertions.assertTrue(noll.removeIf(value -> value == 63L || value == 64L || value == 128L));
    Assertions.assertEquals(5, noll.size());

    Assertions.assertEquals(62L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertEquals(126L, noll.poll());
    Assertions.assertEquals(127L, noll.poll());
    Assertions.assertEquals(129L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllMatchingCollectionWithDisjointWindowsClearsQueue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue retained = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(128L);

    retained.offer(4096L);
    retained.offer(65536L);

    Assertions.assertTrue(noll.retainAll(retained));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
    Assertions.assertNull(noll.poll());
  }

  @Test
  void addAllPlainCollectionWithMixedDuplicatesAndNewValuesReturnsTrue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);

    Assertions.assertTrue(noll.addAll(Arrays.asList(1L, 64L, 65L, 65536L)));
    Assertions.assertEquals(4, noll.size());

    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeAllPlainCollectionWithMixedPresentAndMissingValuesReturnsTrue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.removeAll(Arrays.asList(2L, 64L, 12345L, 65536L)));
    Assertions.assertEquals(2, noll.size());

    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void iteratorRemoveAcrossWindowsPreservesRemainingValues() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    java.util.Iterator<Long> iterator = noll.iterator();
    while (iterator.hasNext()) {
      Long value = iterator.next();
      if (value == 64L) {
        iterator.remove();
      }
    }

    Assertions.assertEquals(2, noll.size());
    Assertions.assertTrue(noll.contains(1L));
    Assertions.assertFalse(noll.contains(64L));
    Assertions.assertTrue(noll.contains(65536L));
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void forEachVisitsValuesInNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(65536L);
    noll.offer(64L);
    noll.offer(1L);

    List<Long> values = new ArrayList<>();
    noll.forEach(values::add);

    Assertions.assertEquals(Arrays.asList(1L, 64L, 65536L), values);
  }

  @Test
  void containsRejectsNonLongValues() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);

    Assertions.assertFalse(noll.contains(1));
    Assertions.assertFalse(noll.contains("1"));
    Assertions.assertFalse(noll.contains(null));
    Assertions.assertTrue(noll.contains(1L));
  }

  @Test
  void containsAllRejectsNonLongValues() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);

    Assertions.assertFalse(noll.containsAll(Arrays.asList(1L, "64")));
    Assertions.assertFalse(noll.containsAll(Arrays.asList(1L, null)));
    Assertions.assertTrue(noll.containsAll(Arrays.asList(1L, 64L)));
  }

  @Test
  void removeRejectsNonLongValuesWithoutChangingQueue() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);

    Assertions.assertFalse(noll.remove("1"));
    Assertions.assertFalse(noll.remove(null));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllMatchingCollectionReturnsFalseWhenUnchanged() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);
    NaturalOrderedLongQueue retained = build(1, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65536L);

    retained.offer(1L);
    retained.offer(64L);
    retained.offer(65536L);

    Assertions.assertFalse(noll.retainAll(retained));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeIfAllValuesAllowsReuse() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1024L);
    noll.offer(4096L);
    noll.offer(65535L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.removeIf(value -> true));
    Assertions.assertTrue(noll.isEmpty());
    Assertions.assertEquals(0, noll.size());
    Assertions.assertNull(noll.poll());

    noll.offer(257L);
    Assertions.assertEquals(257L, noll.peek());
    Assertions.assertEquals(257L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void retainAllPlainCollectionPreservesOnlyRequestedValues() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(63L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.retainAll(Arrays.asList(64L, 65536L)));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertFalse(noll.contains(1L));
    Assertions.assertFalse(noll.contains(63L));
    Assertions.assertTrue(noll.contains(64L));
    Assertions.assertFalse(noll.contains(65L));
    Assertions.assertTrue(noll.contains(65536L));

    Assertions.assertEquals(64L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void removeAllPlainCollectionRemovesSparseValuesOnly() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(1L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(4096L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.removeAll(Arrays.asList(64L, 4096L)));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertTrue(noll.contains(1L));
    Assertions.assertFalse(noll.contains(64L));
    Assertions.assertTrue(noll.contains(65L));
    Assertions.assertFalse(noll.contains(4096L));
    Assertions.assertTrue(noll.contains(65536L));

    Assertions.assertEquals(1L, noll.poll());
    Assertions.assertEquals(65L, noll.poll());
    Assertions.assertEquals(65536L, noll.poll());
    Assertions.assertTrue(noll.isEmpty());
  }

  @Test
  void containsAllHandlesBoundaryAndSparseValues() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(63L);
    noll.offer(64L);
    noll.offer(65L);
    noll.offer(65536L);

    Assertions.assertTrue(noll.containsAll(Arrays.asList(63L, 64L, 65536L)));
    Assertions.assertFalse(noll.containsAll(Arrays.asList(63L, 128L)));
  }

  @Test
  void toArrayReturnsValuesInNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(65536L);
    noll.offer(1L);
    noll.offer(64L);
    noll.offer(63L);

    Assertions.assertArrayEquals(new Object[] {1L, 63L, 64L, 65536L}, noll.toArray());
  }

  @Test
  void iteratorReturnsValuesInNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongQueue noll = build(0, factory);

    noll.offer(65536L);
    noll.offer(0L);
    noll.offer(64L);
    noll.offer(63L);

    List<Long> values = new ArrayList<>();
    for (Long value : noll) {
      values.add(value);
    }

    Assertions.assertEquals(Arrays.asList(0L, 63L, 64L, 65536L), values);
  }

  @Test
  void fileBackedMultiQueueLifecycleTest() throws IOException {
    final int sessionCount = 10;
    final int totalEvents = 2000;
    final int windowSize = 512;
    final String filename = "./multiQueueTest.bit";
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filename, windowSize);

    try {
      // Phase 1: Allocate queues and distribute events
      NaturalOrderedLongQueue[] queues = new NaturalOrderedLongQueue[sessionCount];

      for (int i = 0; i < sessionCount; i++) {
        queues[i] = build(i, factory);
      }

      for (long eventId = 0; eventId < totalEvents; eventId++) {
        int sessionIndex = (int) (eventId % sessionCount);
        queues[sessionIndex].offer(eventId);
      }

      factory.close();

      // Phase 2: Reload and verify persistence
      factory = new FileBitSetFactoryImpl(filename, windowSize);
      queues = new NaturalOrderedLongQueue[sessionCount];
      for (int i = 0; i < sessionCount; i++) {
        queues[i] = build(i, factory);
      }

      for (int i = 0; i < sessionCount; i++) {
        long expected = i;
        int count = 0;
        while (!queues[i].isEmpty()) {
          Long actual = queues[i].poll();
          Assertions.assertEquals(expected, actual);
          expected += sessionCount;
          count++;
        }
        Assertions.assertTrue(count > 0);
      }

      // Phase 3: Release a few queues, verify increase in free list
      int initialFreeSize = factory.get(-1).size();

      // Release half the queues
      for (int i = 0; i < sessionCount; i += 2) {
        queues[i].clear();
      }

      int increasedFreeSize = factory.get(-1).size();
      Assertions.assertTrue(increasedFreeSize > initialFreeSize);

      // Phase 4: Reuse a closed session ID and verify it is empty
      NaturalOrderedLongQueue reusedQueue = build(0, factory);
      Assertions.assertTrue(reusedQueue.isEmpty());
      reusedQueue.offer(99999L);
      Assertions.assertEquals(99999, reusedQueue.poll());

    } finally {
      factory.close();
      Files.deleteIfExists(Paths.get(filename));
    }
  }
}