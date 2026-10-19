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

import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicLong;

class NaturalOrderedLongListTest {

  @Test
  void constructors() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      new NaturalOrderedLongList(0, null);
    });
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(4096);
    Assertions.assertDoesNotThrow(() -> {
      new NaturalOrderedLongList(0, factory);
    });
  }

  @Test
  void testSingleBitSetIterator() {
    listIterator(4096);
  }

  @Test
  void testMultipleBitSetIterator() {
    listIterator(128);
  }

  @Test
  void emptyListIteratorHasNoNextOrPrevious() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    ListIterator<Long> iterator = noll.listIterator();

    Assertions.assertFalse(iterator.hasNext());
    Assertions.assertFalse(iterator.hasPrevious());
    Assertions.assertNull(iterator.next());
    Assertions.assertNull(iterator.previous());
  }

  @Test
  void listIteratorOnClearedListHasNoNextOrPrevious() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(1L);
    noll.add(64L);
    noll.clear();

    ListIterator<Long> iterator = noll.listIterator();

    Assertions.assertFalse(iterator.hasNext());
    Assertions.assertFalse(iterator.hasPrevious());
    Assertions.assertNull(iterator.next());
    Assertions.assertNull(iterator.previous());
  }

  @Test
  void unsupportedFunctions() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(4096);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.get(1);
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.set(1, 4L);
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.add(1, 4L);
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.remove(1);
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.indexOf(5L);
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.lastIndexOf(5L);
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.listIterator(5);
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      noll.subList(5, 10);
    });
  }

  @Test
  void arrayFunctionsSingleBitSet() {
    arrayFunctions(4096);
  }

  @Test
  void arrayFunctionsMultipleBitSet() {
    arrayFunctions(128);
  }

  @Test
  void allAPICollectionsSingleBitSet() {
    allAPICollections(4096);
  }

  @Test
  void allAPICollectionsMultipleBitSet() {
    allAPICollections(128);
  }

  @Test
  void allAPICollectionsSingleBitSetCopy() {
    addAllWithBitSet(4096);
  }

  @Test
  void allAPICollectionsMultipleBitSetCopy() {
    addAllWithBitSet(128);
  }

  @Test
  void checkForEach() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(128);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    long sum = 0;
    for (long x = 0; x < 1000; x++) {
      noll.add(x);
      sum += x;
    }
    AtomicLong forEachSum = new AtomicLong();
    noll.forEach(forEachSum::addAndGet);
    Assertions.assertEquals(sum, forEachSum.get());
  }

  @Test
  void checkRemoveIf() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(128);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    for (long x = 0; x < 1000; x++) {
      noll.add(x);
    }
    noll.removeIf(aLong -> aLong % 2 == 0);
    Assertions.assertEquals(500, noll.size());
  }

  @Test
  void listIteratorAddPreservesNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(64L);
    noll.add(1L);

    ListIterator<Long> iterator = noll.listIterator();
    iterator.add(63L);
    iterator.add(65536L);

    Assertions.assertArrayEquals(new Object[] {1L, 63L, 64L, 65536L}, noll.toArray());
  }

  @Test
  void listIteratorWalksBackwardsAcrossBoundaryAndSparseValues() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(1L);
    noll.add(64L);
    noll.add(65L);
    noll.add(65536L);

    ListIterator<Long> iterator = noll.listIterator();
    while (iterator.hasNext()) {
      iterator.next();
    }

    Assertions.assertTrue(iterator.hasPrevious());
    Assertions.assertEquals(65536L, iterator.previous());
    Assertions.assertTrue(iterator.hasPrevious());
    Assertions.assertEquals(65L, iterator.previous());
    Assertions.assertTrue(iterator.hasPrevious());
    Assertions.assertEquals(64L, iterator.previous());
    Assertions.assertTrue(iterator.hasPrevious());
    Assertions.assertEquals(1L, iterator.previous());
    Assertions.assertFalse(iterator.hasPrevious());
  }

  @Test
  void addAllWithIndexAddsInNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    Assertions.assertTrue(noll.addAll(0, Arrays.asList(64L, 1L, 65536L)));

    Assertions.assertArrayEquals(new Object[] {1L, 64L, 65536L}, noll.toArray());
  }

  @Test
  void addAllWithIndexReturnsFalseWhenOnlyAddingDuplicates() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(1L);
    noll.add(64L);

    Assertions.assertFalse(noll.addAll(0, Arrays.asList(1L, 64L)));
    Assertions.assertArrayEquals(new Object[] {1L, 64L}, noll.toArray());
  }

  @Test
  void retainAllMatchingCollectionRemovesValuesFromMissingWindows() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    NaturalOrderedLongList retained = new NaturalOrderedLongList(1, factory);

    noll.add(1L);
    noll.add(65536L);

    retained.add(1L);

    Assertions.assertTrue(noll.retainAll(retained));
    Assertions.assertEquals(1, noll.size());
    Assertions.assertTrue(noll.contains(1L));
    Assertions.assertFalse(noll.contains(65536L));
    Assertions.assertArrayEquals(new Object[] {1L}, noll.toArray());
  }

  @Test
  void addAllReturnsFalseWhenAddingOnlyDuplicates() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(1L);
    noll.add(64L);

    Assertions.assertFalse(noll.addAll(Arrays.asList(1L, 64L)));
    Assertions.assertEquals(2, noll.size());
    Assertions.assertArrayEquals(new Object[] {1L, 64L}, noll.toArray());
  }

  @Test
  void removeAllReturnsFalseWhenNothingRemoved() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(1L);
    noll.add(64L);
    noll.add(65536L);

    Assertions.assertFalse(noll.removeAll(Arrays.asList(2L, 65L)));
    Assertions.assertEquals(3, noll.size());
    Assertions.assertArrayEquals(new Object[] {1L, 64L, 65536L}, noll.toArray());
  }

  @Test
  void removeIfRemovesBoundarySubsetOnly() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(62L);
    noll.add(63L);
    noll.add(64L);
    noll.add(65L);
    noll.add(126L);
    noll.add(127L);
    noll.add(128L);
    noll.add(129L);

    Assertions.assertTrue(noll.removeIf(value -> value == 63L || value == 64L || value == 128L));
    Assertions.assertEquals(5, noll.size());
    Assertions.assertArrayEquals(new Object[] {62L, 65L, 126L, 127L, 129L}, noll.toArray());
  }

  @Test
  void toArrayGeneratorReturnsValuesInNaturalOrder() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);

    noll.add(65536L);
    noll.add(1L);
    noll.add(64L);
    noll.add(63L);

    Long[] values = noll.toArray(Long[]::new);

    Assertions.assertArrayEquals(new Long[] {1L, 63L, 64L, 65536L}, values);
  }

  void addAllWithBitSet(int bitSetSize) {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(bitSetSize);
    NaturalOrderedLongList list = new NaturalOrderedLongList(0, factory);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    for (long x = 0; x < 1000; x++) {
      list.add(x);
    }

    noll.addAll(list);
    Assertions.assertEquals(1000, noll.size());

    Assertions.assertTrue(noll.containsAll(list));

    Assertions.assertFalse(noll.retainAll(list));
    Assertions.assertEquals(1000, noll.size());

    noll.removeAll(list);
    Assertions.assertEquals(0, noll.size());

    noll.addAll(list);
    Assertions.assertEquals(1000, noll.size());

    noll.clear();
    Assertions.assertEquals(0, noll.size());

    noll.addAll(0, list);
    Assertions.assertEquals(1000, noll.size());

    list.clear();
    for (long x = 0; x < 1000; x++) {
      if (x % 2 == 0) {
        list.add(x);
      }
    }
    noll.retainAll(list);
    Assertions.assertEquals(list.size(), noll.size());
    Assertions.assertTrue(noll.containsAll(list));
  }

  private void allAPICollections(int bitSetSize) {
    List<Long> list = new ArrayList<>();
    for (long x = 0; x < 1000; x++) {
      list.add(x);
    }
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(bitSetSize);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    noll.addAll(list);
    Assertions.assertEquals(1000, noll.size());

    Assertions.assertTrue(noll.containsAll(list));

    Assertions.assertFalse(noll.retainAll(list));
    Assertions.assertEquals(1000, noll.size());

    noll.removeAll(list);
    Assertions.assertEquals(0, noll.size());

    noll.addAll(list);
    Assertions.assertEquals(1000, noll.size());

    noll.clear();
    Assertions.assertEquals(0, noll.size());

    noll.addAll(0, list);
    Assertions.assertEquals(1000, noll.size());

    list.clear();
    for (long x = 0; x < 1000; x++) {
      if (x % 2 == 0) {
        list.add(x);
      }
    }
    noll.retainAll(list);
    Assertions.assertEquals(list.size(), noll.size());
    Assertions.assertTrue(noll.containsAll(list));
  }

  private void arrayFunctions(int bitSetSize) {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(bitSetSize);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    for (long x = 0; x < 1000; x++) {
      noll.add(x);
    }
    Object[] array = noll.toArray();
    Assertions.assertEquals(1000, array.length);
    for (int x = 0; x < 1000; x++) {
      Assertions.assertEquals(x, ((Long) array[x]));
    }
    Long[] longs = new Long[noll.size()];
    longs = noll.toArray(longs);
    Assertions.assertEquals(1000, longs.length);
    for (int x = 0; x < 1000; x++) {
      Assertions.assertEquals(x, longs[x]);
    }
    longs = noll.toArray(Long[]::new);
    Assertions.assertEquals(1000, longs.length);
    for (int x = 0; x < 1000; x++) {
      Assertions.assertEquals(x, longs[x]);
    }
  }

  private void listIterator(int bitSetSize) {
    List<Long> list = new ArrayList<>();
    for (long x = 0; x < 1000; x++) {
      list.add(x);
    }
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(bitSetSize);
    NaturalOrderedLongList noll = new NaturalOrderedLongList(0, factory);
    noll.addAll(list);
    Assertions.assertEquals(1000, noll.size());

    ListIterator<Long> listIterator = noll.listIterator();
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      listIterator.nextIndex();
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      listIterator.previousIndex();
    });
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      listIterator.set(12L);
    });

    long expected = 0;
    while (listIterator.hasNext()) {
      Assertions.assertEquals(expected, listIterator.next());
      expected++;
    }
    Assertions.assertNull(listIterator.next());
    listIterator.add(expected);
    expected++;
    Assertions.assertEquals(expected, noll.size());

    expected--;
    while (listIterator.hasPrevious()) {
      Assertions.assertEquals(expected, listIterator.previous());
      expected--;
    }
    Assertions.assertEquals(-1, expected);
    Assertions.assertNull(listIterator.previous());

    expected = 0;
    ListIterator<Long> listIterator2 = noll.listIterator();
    while (listIterator2.hasNext()) {
      Assertions.assertEquals(expected, listIterator2.next());
      listIterator2.remove();
      expected++;
    }
    Assertions.assertEquals(0, noll.size());
  }
}