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

package io.mapsmessaging.utilities.collections.bitset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.ListIterator;

public abstract class OffsetBitSetTest {

  public abstract BitSet createBitSet();

  public OffsetBitSet createOffsetBitset(long offset) {
    return new OffsetBitSet(createBitSet(), offset);
  }

  @Test
  public void testBasicFunctions() {
    long offset = 1L << 32;
    OffsetBitSet offsetBitSet = createOffsetBitset(offset);

    for (int x = 0; x < 100; x++) {
      offsetBitSet.set(offset + x);
      Assertions.assertTrue(offsetBitSet.isSet(offset + x));
    }

    Iterator<Long> itr = offsetBitSet.iterator();
    long start = offset;
    while (itr.hasNext()) {
      Assertions.assertEquals(start, itr.next());
      start++;
    }
    Assertions.assertEquals(offset + 100, start);
  }

  @Test
  public void testGeneralFunctions() {
    long offset = 64;
    OffsetBitSet offsetBitSet = createOffsetBitset(offset);

    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> offsetBitSet.set(0));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> offsetBitSet.set(offset + offsetBitSet.length()));

    offsetBitSet.set(offset);
    Assertions.assertTrue(offsetBitSet.isSet(offset));

    offsetBitSet.set(offset + offsetBitSet.length() - 1);
    Assertions.assertTrue(offsetBitSet.isSet(offset + offsetBitSet.length() - 1));
  }

  @Test
  public void testSimpleBitOperations() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    bitmap.set(0);
    Assertions.assertTrue(bitmap.isSet(0));
    bitmap.set(63);
    Assertions.assertTrue(bitmap.isSet(63));
    bitmap.set(64);
    Assertions.assertTrue(bitmap.isSet(64));
    bitmap.set(127);
    Assertions.assertTrue(bitmap.isSet(127));
    bitmap.set(128);
    Assertions.assertTrue(bitmap.isSet(128));

    bitmap.clear(0);
    Assertions.assertFalse(bitmap.isSet(0));
    bitmap.clear(63);
    Assertions.assertFalse(bitmap.isSet(63));
    Assertions.assertTrue(bitmap.isSet(64));
    Assertions.assertTrue(bitmap.isSet(127));
    Assertions.assertTrue(bitmap.isSet(128));

    bitmap.clear();

    bitmap.set(1);
    Assertions.assertTrue(bitmap.isSet(1));
    Assertions.assertFalse(bitmap.isSet(0));
    Assertions.assertFalse(bitmap.isSet(2));
    bitmap.flip(1);
    Assertions.assertFalse(bitmap.isSet(0));
    Assertions.assertFalse(bitmap.isSet(1));
    Assertions.assertFalse(bitmap.isSet(2));

    bitmap.flip(1);
    Assertions.assertTrue(bitmap.isSet(1));
    Assertions.assertFalse(bitmap.isSet(0));
    Assertions.assertFalse(bitmap.isSet(2));
    bitmap.flip(1);
    Assertions.assertFalse(bitmap.isSet(0));
    Assertions.assertFalse(bitmap.isSet(1));
    Assertions.assertFalse(bitmap.isSet(2));
  }

  @Test
  public void testClearing() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    int bitCount = bitmap.length();
    for (int x = 0; x < bitCount; x++) {
      bitmap.set(x);
      Assertions.assertTrue(bitmap.isSet(x));
    }
    Assertions.assertFalse(bitmap.isEmpty());
    bitmap.clear();
    for (int x = 0; x < bitCount; x++) {
      Assertions.assertFalse(bitmap.isSet(x));
    }
    Assertions.assertTrue(bitmap.isEmpty());
  }

  @Test
  public void testNextSetAndClear() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    int bitCount = bitmap.length();
    for (int x = 0; x < bitCount; x++) {
      bitmap.set(x);
      Assertions.assertTrue(bitmap.isSet(x));
    }
    for (int x = 0; x < bitCount; x++) {
      Assertions.assertNotEquals(-1, bitmap.nextSetBitAndClear(0));
    }
    for (int x = 0; x < bitCount; x++) {
      Assertions.assertFalse(bitmap.isSet(x));
    }
  }

  @Test
  public void testFlipping() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    int bitCount = bitmap.length();
    for (int x = 0; x < bitCount; x++) {
      bitmap.flip(x);
      Assertions.assertTrue(bitmap.isSet(x));
    }
    for (int x = 0; x < bitCount; x++) {
      bitmap.flip(x);
      Assertions.assertFalse(bitmap.isSet(x));
    }
  }

  @Test
  void testFlipRange() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    int bits = bitmap.length();

    for (int i = 0; i < bits; i++) {
      Assertions.assertFalse(bitmap.isSet(i));
    }

    bitmap.flip(10, 20);

    for (int i = 0; i < bits; i++) {
      if (i >= 10 && i < 20) {
        Assertions.assertTrue(bitmap.isSet(i), "Expected bit " + i + " to be set");
      } else {
        Assertions.assertFalse(bitmap.isSet(i), "Expected bit " + i + " to be clear");
      }
    }

    bitmap.flip(10, 20);

    for (int i = 0; i < bits; i++) {
      Assertions.assertFalse(bitmap.isSet(i), "Expected bit " + i + " to be clear after second flip");
    }
  }

  @Test
  public void testRangedFlipping() {
    OffsetBitSet bitmap = createOffsetBitset(0);

    int bitCount = bitmap.length();
    bitmap.flip(1, 24);
    Assertions.assertFalse(bitmap.isSet(0));

    for (int x = 1; x < 24; x++) {
      Assertions.assertTrue(bitmap.isSet(x));
    }

    for (int x = 24; x < bitCount; x++) {
      Assertions.assertFalse(bitmap.isSet(x));
    }
  }

  @Test
  public void testFindSetBit() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    bitmap.set(0);
    Assertions.assertEquals(1, bitmap.cardinality());
    bitmap.set(63);
    Assertions.assertEquals(2, bitmap.cardinality());
    bitmap.set(64);
    Assertions.assertEquals(3, bitmap.cardinality());
    bitmap.set(127);
    Assertions.assertEquals(4, bitmap.cardinality());
    bitmap.set(128);
    Assertions.assertEquals(5, bitmap.cardinality());
    Assertions.assertEquals(0, bitmap.nextSetBit(0));
    bitmap.clear(0);
    Assertions.assertEquals(63, bitmap.nextSetBit(0));
    bitmap.clear(63);
    Assertions.assertEquals(64, bitmap.nextSetBit(0));
    bitmap.clear(64);
    Assertions.assertEquals(127, bitmap.nextSetBit(0));
    bitmap.clear(127);
    Assertions.assertEquals(128, bitmap.nextSetBit(0));
    bitmap.clear(128);
    Assertions.assertEquals(-1, bitmap.nextSetBit(0));
  }

  @Test
  public void testFindClearBit() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    int bitCount = bitmap.length();
    for (int x = 0; x < bitCount; x++) {
      bitmap.set(x);
    }
    bitmap.clear(128);
    Assertions.assertEquals(128, bitmap.nextClearBit(0));
  }

  @Test
  public void testFindPreviousSetBit() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    bitmap.set(128);
    Assertions.assertEquals(1, bitmap.cardinality());
    int bitCount = bitmap.length() - 1;
    Assertions.assertEquals(128, bitmap.previousSetBit(bitCount));
  }

  @Test
  public void testFindPreviousClearBit() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    int bitCount = bitmap.length();
    for (int x = 0; x < bitCount; x++) {
      bitmap.set(x);
    }

    bitmap.clear(128);
    Assertions.assertEquals(bitmap.length() - 1, bitmap.cardinality());
    bitCount = bitmap.length() - 1;
    Assertions.assertEquals(128, bitmap.previousClearBit(bitCount));
  }

  @Test
  public void testBitWiseANDOperations() {
    OffsetBitSet bitmap1 = createOffsetBitset(0);
    OffsetBitSet bitmap2 = createOffsetBitset(0);

    int bitCount = bitmap1.length();
    for (int x = 0; x < bitCount; x++) {
      if (x % 2 == 0) {
        bitmap1.set(x);
      } else {
        bitmap2.set(x);
      }
    }

    bitmap1.and(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      Assertions.assertFalse(bitmap1.isSet(x));
    }

    bitmap1.clear();
    bitmap2.clear();
    for (int x = 0; x < bitCount; x++) {
      int test = x % 3;
      if (test == 0) {
        bitmap1.set(x);
      } else if (test == 1) {
        bitmap2.set(x);
      } else {
        bitmap1.set(x);
        bitmap2.set(x);
      }
    }

    bitmap1.and(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      if (x % 3 == 2) {
        Assertions.assertTrue(bitmap1.isSet(x));
      } else {
        Assertions.assertFalse(bitmap1.isSet(x));
      }
    }
  }

  @Test
  public void testBitWiseOROperations() {
    OffsetBitSet bitmap1 = createOffsetBitset(0);
    OffsetBitSet bitmap2 = createOffsetBitset(0);

    int bitCount = bitmap1.length();
    for (int x = 0; x < bitCount; x++) {
      if (x % 2 == 0) {
        bitmap1.set(x);
      } else {
        bitmap2.set(x);
      }
    }

    bitmap1.or(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      Assertions.assertTrue(bitmap1.isSet(x));
    }

    bitmap1.clear();
    bitmap2.clear();
    for (int x = 0; x < bitCount; x++) {
      int test = x % 3;
      if (test == 0) {
        bitmap1.set(x);
      } else if (test == 1) {
        bitmap2.set(x);
      }
    }

    bitmap1.or(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      if (x % 3 == 2) {
        Assertions.assertFalse(bitmap1.isSet(x));
      } else {
        Assertions.assertTrue(bitmap1.isSet(x));
      }
    }
  }

  @Test
  public void testListIterator() {
    OffsetBitSet bitSet = createOffsetBitset(0);

    int[] test = {0, 2, 5, 10, 100, 200};

    for (int set : test) {
      bitSet.set(set);
    }

    ListIterator<Long> itr = bitSet.listIterator();
    for (int i : test) {
      Assertions.assertTrue(itr.hasNext());
      Assertions.assertEquals(i, itr.next());
    }

    Assertions.assertFalse(itr.hasNext());
    for (int i = test.length - 1; i > 0; i--) {
      Assertions.assertTrue(itr.hasPrevious());
      Assertions.assertEquals(test[i], itr.previous());
    }
  }

  @Test
  public void testBitWiseXOROperations() {
    OffsetBitSet bitmap1 = createOffsetBitset(0);
    OffsetBitSet bitmap2 = createOffsetBitset(0);

    int bitCount = bitmap1.length();
    for (int x = 0; x < bitCount; x++) {
      if (x % 2 == 0) {
        bitmap1.set(x);
      } else {
        bitmap2.set(x);
      }
    }

    bitmap1.xor(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      Assertions.assertTrue(bitmap1.isSet(x));
    }

    bitmap1.clear();
    bitmap2.clear();
    for (int x = 0; x < bitCount; x++) {
      int test = x % 4;
      if (test == 0) {
        bitmap1.set(x);
      } else if (test == 1) {
        bitmap2.set(x);
      } else if (test == 2) {
        bitmap1.set(x);
        bitmap2.set(x);
      }
    }

    bitmap1.xor(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      if (x % 4 == 0 || x % 4 == 1) {
        Assertions.assertTrue(bitmap1.isSet(x));
      } else {
        Assertions.assertFalse(bitmap1.isSet(x));
      }
    }
  }

  @Test
  public void testBitWiseANDNOTOperations() {
    OffsetBitSet bitmap1 = createOffsetBitset(0);
    OffsetBitSet bitmap2 = createOffsetBitset(0);

    int bitCount = bitmap1.length();
    for (int x = 0; x < bitCount; x++) {
      if (x % 2 == 0) {
        bitmap1.set(x);
      } else {
        bitmap2.set(x);
      }
    }

    bitmap1.andNot(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      if (x % 2 == 0) {
        Assertions.assertTrue(bitmap1.isSet(x));
      } else {
        Assertions.assertFalse(bitmap1.isSet(x));
      }
    }

    bitmap1.clear();
    bitmap2.clear();
    for (int x = 0; x < bitCount; x++) {
      int test = x % 4;
      if (test == 0) {
        bitmap1.set(x);
      } else if (test == 1) {
        bitmap2.set(x);
      } else if (test == 2) {
        bitmap1.set(x);
        bitmap2.set(x);
      } else {
        bitmap1.clear(x);
        bitmap2.clear(x);
      }
    }

    bitmap1.andNot(bitmap2.rawBitSet);
    for (int x = 0; x < bitCount; x++) {
      if (x % 4 == 0) {
        Assertions.assertTrue(bitmap1.isSet(x));
      } else {
        Assertions.assertFalse(bitmap1.isSet(x));
      }
    }
  }

  @Test
  public void testExceptions() {
    OffsetBitSet bitmap = createOffsetBitset(0);

    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.set(-1));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.set(bitmap.length()));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.clear(-1));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.clear(bitmap.length()));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(-1));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(bitmap.length()));
  }

  @Test
  public void testIterator() {
    OffsetBitSet bitmap = createOffsetBitset(0);

    int[] test = {0, 2, 5, 10, 100, 200};

    for (int set : test) {
      bitmap.set(set);
    }

    Iterator<Long> iterator = bitmap.iterator();
    int idx = 0;
    while (iterator.hasNext()) {
      Assertions.assertEquals(test[idx], iterator.next());
      idx++;
    }
    Assertions.assertEquals(test.length, idx);

    bitmap.clear();
    for (int x = 0; x < bitmap.length(); x++) {
      bitmap.set(x);
    }

    iterator = bitmap.iterator();
    idx = 0;
    while (iterator.hasNext()) {
      long next = iterator.next();
      Assertions.assertEquals(idx, next);
      idx++;
    }

    bitmap = createOffsetBitset(0);

    for (int x = 0; x < bitmap.length(); x++) {
      bitmap.set(x);
    }

    iterator = bitmap.iterator();
    idx = 0;
    while (iterator.hasNext()) {
      long next = iterator.next();
      Assertions.assertEquals(idx, next);
      idx++;
    }
  }

  @Test
  public void checkGetFunctions() {
    OffsetBitSet bitmap = createOffsetBitset(0);
    Assertions.assertEquals(0, bitmap.getStart());
    Assertions.assertEquals(bitmap.length(), bitmap.getEnd());
  }

  @Test
  public void offsetStartAndEndReflectWindow() {
    OffsetBitSet bitmap = createOffsetBitset(64);

    Assertions.assertEquals(64, bitmap.getStart());
    Assertions.assertEquals(64 + bitmap.length(), bitmap.getEnd());
  }

  @Test
  public void setClearAndIsSetUseExternalOffsetValues() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    Assertions.assertTrue(bitmap.set(offset));
    Assertions.assertTrue(bitmap.isSet(offset));
    Assertions.assertFalse(bitmap.isSet(offset + 1));

    Assertions.assertTrue(bitmap.set(offset + 63));
    Assertions.assertTrue(bitmap.isSet(offset + 63));

    Assertions.assertTrue(bitmap.clear(offset));
    Assertions.assertFalse(bitmap.isSet(offset));
    Assertions.assertTrue(bitmap.isSet(offset + 63));
  }

  @Test
  public void boundaryChecksUseExternalOffsetValues() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.set(offset - 1));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.set(bitmap.getEnd()));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.clear(offset - 1));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.clear(bitmap.getEnd()));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(offset - 1));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(bitmap.getEnd()));
  }

  @Test
  public void nextSetBitReturnsExternalValues() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    bitmap.set(offset + 1);
    bitmap.set(offset + 63);

    Assertions.assertEquals(offset + 1, bitmap.nextSetBit(offset));
    Assertions.assertEquals(offset + 1, bitmap.nextSetBit(offset + 1));
    Assertions.assertEquals(offset + 63, bitmap.nextSetBit(offset + 2));
    Assertions.assertEquals(-1, bitmap.nextSetBit(offset + 64));
  }

  @Test
  public void nextSetBitAndClearReturnsExternalValuesAndClears() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    bitmap.set(offset + 1);
    bitmap.set(offset + 63);

    Assertions.assertEquals(offset + 1, bitmap.nextSetBitAndClear(offset));
    Assertions.assertFalse(bitmap.isSet(offset + 1));
    Assertions.assertEquals(offset + 63, bitmap.nextSetBitAndClear(offset));
    Assertions.assertFalse(bitmap.isSet(offset + 63));
    Assertions.assertEquals(-1, bitmap.nextSetBitAndClear(offset));
  }

  @Test
  public void previousSetBitReturnsMinusOneWhenNoSetBitExists() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    Assertions.assertEquals(-1, bitmap.previousSetBit(offset));
  }

  @Test
  public void previousSetBitReturnsExternalValues() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    bitmap.set(offset);
    bitmap.set(offset + 63);

    Assertions.assertEquals(offset, bitmap.previousSetBit(offset));
    Assertions.assertEquals(offset + 63, bitmap.previousSetBit(offset + 63));
    Assertions.assertEquals(offset, bitmap.previousSetBit(offset + 62));
  }

  @Test
  public void previousClearBitReturnsMinusOneWhenNoClearBitExists() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    for (long value = offset; value < bitmap.getEnd(); value++) {
      bitmap.set(value);
    }

    Assertions.assertEquals(-1, bitmap.previousClearBit(offset));
  }

  @Test
  public void previousClearBitReturnsExternalValues() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    for (long value = offset; value < bitmap.getEnd(); value++) {
      bitmap.set(value);
    }

    bitmap.clear(offset);
    bitmap.clear(offset + 63);

    Assertions.assertEquals(offset, bitmap.previousClearBit(offset));
    Assertions.assertEquals(offset + 63, bitmap.previousClearBit(offset + 63));
    Assertions.assertEquals(offset, bitmap.previousClearBit(offset + 62));
  }

  @Test
  public void nextClearBitReturnsExternalValues() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    for (long value = offset; value < bitmap.getEnd(); value++) {
      bitmap.set(value);
    }

    bitmap.clear(offset + 10);

    Assertions.assertEquals(offset + 10, bitmap.nextClearBit(offset));
  }

  @Test
  public void flipRangeCanUseEndAsExclusiveBoundary() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    bitmap.flip(offset, bitmap.getEnd());

    Assertions.assertEquals(bitmap.length(), bitmap.cardinality());
    for (long value = offset; value < bitmap.getEnd(); value++) {
      Assertions.assertTrue(bitmap.isSet(value));
    }

    bitmap.flip(offset, bitmap.getEnd());

    Assertions.assertTrue(bitmap.isEmpty());
  }

  @Test
  public void iteratorReturnsExternalValues() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    long[] values = {offset, offset + 2, offset + 63, offset + 128};
    for (long value : values) {
      bitmap.set(value);
    }

    Iterator<Long> iterator = bitmap.iterator();
    int index = 0;
    while (iterator.hasNext()) {
      Assertions.assertEquals(values[index], iterator.next());
      index++;
    }
    Assertions.assertEquals(values.length, index);
  }

  @Test
  public void listIteratorReturnsExternalValuesInBothDirections() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    long[] values = {offset, offset + 2, offset + 63, offset + 128};
    for (long value : values) {
      bitmap.set(value);
    }

    ListIterator<Long> iterator = bitmap.listIterator();
    for (long value : values) {
      Assertions.assertTrue(iterator.hasNext());
      Assertions.assertEquals(value, iterator.next());
    }

    for (int index = values.length - 1; index > 0; index--) {
      Assertions.assertTrue(iterator.hasPrevious());
      Assertions.assertEquals(values[index], iterator.previous());
    }
  }

  @Test
  public void iteratorRemoveClearsReturnedExternalValue() {
    long offset = 64;
    OffsetBitSet bitmap = createOffsetBitset(offset);

    bitmap.set(offset);
    bitmap.set(offset + 1);

    Iterator<Long> iterator = bitmap.iterator();

    Assertions.assertEquals(offset, iterator.next());
    iterator.remove();

    Assertions.assertFalse(bitmap.isSet(offset));
    Assertions.assertTrue(bitmap.isSet(offset + 1));
  }

  @Test
  public void resetChangesStartEndUniqueIdAndClearsBits() {
    OffsetBitSet bitmap = createOffsetBitset(0);

    bitmap.set(1);
    bitmap.reset(64, 123L);

    Assertions.assertEquals(64, bitmap.getStart());
    Assertions.assertEquals(64 + bitmap.length(), bitmap.getEnd());
    Assertions.assertEquals(123L, bitmap.getBitSet().getUniqueId());
    Assertions.assertTrue(bitmap.isEmpty());

    bitmap.set(64);
    Assertions.assertTrue(bitmap.isSet(64));
  }

  @Test
  public void releaseBitSetMakesInstanceInactive() {
    OffsetBitSet bitmap = createOffsetBitset(0);

    bitmap.set(1);
    bitmap.releaseBitSet();

    Assertions.assertFalse(bitmap.isActive());
    Assertions.assertThrows(IllegalStateException.class, () -> bitmap.set(1));
    Assertions.assertThrows(IllegalStateException.class, bitmap::isEmpty);
    Assertions.assertThrows(IllegalStateException.class, bitmap::getBitSet);
    Assertions.assertEquals("cleared - unusable", bitmap.toString());
  }

  @Test
  public void compareToAndEqualsUseStartOnly() {
    OffsetBitSet first = createOffsetBitset(64);
    OffsetBitSet sameStart = createOffsetBitset(64);
    OffsetBitSet later = createOffsetBitset(128);

    Assertions.assertEquals(0, first.compareTo(sameStart));
    Assertions.assertTrue(first.compareTo(later) < 0);
    Assertions.assertTrue(later.compareTo(first) > 0);
    Assertions.assertEquals(first, sameStart);
    Assertions.assertNotEquals(first, later);
  }
}