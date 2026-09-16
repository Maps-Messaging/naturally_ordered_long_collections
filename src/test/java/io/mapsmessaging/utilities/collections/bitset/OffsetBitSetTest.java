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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public abstract class OffsetBitSetTest {

  public abstract BitSet createBitSet();

  public OffsetBitSet createOffsetBitset(long offset) {
    return new OffsetBitSet(createBitSet(), offset);
  }

  @Test
  void supportsOffsetsBeyondIntegerRange() {
    long offset = 1L << 32;
    OffsetBitSet bitSet = createOffsetBitset(offset);

    for (int i = 0; i < 100; i++) {
      bitSet.set(offset + i);
      Assertions.assertTrue(bitSet.isSet(offset + i));
    }

    long expected = offset;
    for (long actual : bitSet) {
      Assertions.assertEquals(expected++, actual);
    }
  }

  @Test
  void supportsNegativeOffsetsAndMinusOne() {
    OffsetBitSet bitSet = createOffsetBitset(-8192L);
    bitSet.set(-8192L);
    bitSet.set(-4096L);
    bitSet.set(-1L);

    Assertions.assertEquals(Long.valueOf(-8192L), bitSet.nextSetBit(Long.MIN_VALUE));
    Assertions.assertEquals(Long.valueOf(-4096L), bitSet.nextSetBit(-8191L));
    Assertions.assertEquals(Long.valueOf(-1L), bitSet.nextSetBit(-4095L));
    Assertions.assertNull(bitSet.nextSetBit(0L));
    Assertions.assertTrue(bitSet.isSet(-1L));
  }

  @Test
  void supportsLongMinValueWindow() {
    OffsetBitSet bitSet = createOffsetBitset(Long.MIN_VALUE);
    bitSet.set(Long.MIN_VALUE);
    bitSet.set(Long.MIN_VALUE + bitSet.length() - 1L);

    Assertions.assertEquals(Long.valueOf(Long.MIN_VALUE), bitSet.nextSetBit(Long.MIN_VALUE));
    Assertions.assertEquals(
        Long.valueOf(Long.MIN_VALUE + bitSet.length() - 1L),
        bitSet.previousSetBit(Long.MIN_VALUE + bitSet.length() - 1L));
  }

  @Test
  void supportsLongMaxValue() {
    int physicalLength = createBitSet().length();
    long start = Long.MAX_VALUE - physicalLength + 1L;
    OffsetBitSet bitSet = createOffsetBitset(start);
    bitSet.set(start);
    bitSet.set(Long.MAX_VALUE);

    Assertions.assertEquals(Long.valueOf(start), bitSet.nextSetBit(start));
    Assertions.assertEquals(Long.valueOf(Long.MAX_VALUE), bitSet.previousSetBit(Long.MAX_VALUE));
    Assertions.assertTrue(bitSet.isSet(Long.MAX_VALUE));
  }

  @Test
  void truncatesLogicalWindowAtLongMaxValue() {
    OffsetBitSet bitSet = createOffsetBitset(Long.MAX_VALUE);
    Assertions.assertEquals(1, bitSet.length());
    Assertions.assertTrue(bitSet.set(Long.MAX_VALUE));
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitSet.set(Long.MAX_VALUE - 1));
  }

  @Test
  void rejectsValuesOutsideWindowWithoutOverflow() {
    OffsetBitSet bitSet = createOffsetBitset(64L);
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitSet.set(63L));
    Assertions.assertThrows(
        IndexOutOfBoundsException.class,
        () -> bitSet.set(64L + bitSet.length()));
  }

  @Test
  void absoluteSearchUsesNullForNoResult() {
    OffsetBitSet bitSet = createOffsetBitset(-64L);
    Assertions.assertNull(bitSet.nextSetBit(-64L));
    Assertions.assertNull(bitSet.previousSetBit(-64L));

    bitSet.set(-1L);
    Assertions.assertEquals(Long.valueOf(-1L), bitSet.nextSetBit(-64L));
    Assertions.assertEquals(Long.valueOf(-1L), bitSet.nextSetBitAndClear(-64L));
    Assertions.assertNull(bitSet.nextSetBit(-64L));
  }

  @Test
  void clearFlipAndRangeOperationsUseAbsoluteValues() {
    OffsetBitSet bitSet = createOffsetBitset(-1000L);
    bitSet.set(-999L);
    Assertions.assertTrue(bitSet.isSet(-999L));
    Assertions.assertTrue(bitSet.clear(-999L));
    Assertions.assertFalse(bitSet.isSet(-999L));

    bitSet.flip(-990L);
    Assertions.assertTrue(bitSet.isSet(-990L));
    bitSet.flip(-990L);
    Assertions.assertFalse(bitSet.isSet(-990L));

    bitSet.flip(-980L, -970L);
    for (long value = -980L; value < -970L; value++) {
      Assertions.assertTrue(bitSet.isSet(value));
    }
    Assertions.assertFalse(bitSet.isSet(-969L));
  }

  @Test
  void nextAndPreviousClearBitStayWithinLogicalWindow() {
    OffsetBitSet bitSet = createOffsetBitset(-128L);
    for (int i = 0; i < bitSet.length(); i++) {
      bitSet.set(-128L + i);
    }
    bitSet.clear(-64L);

    Assertions.assertEquals(Long.valueOf(-64L), bitSet.nextClearBit(-128L));
    Assertions.assertEquals(Long.valueOf(-64L), bitSet.previousClearBit(-1L));
  }

  @Test
  void bitwiseOperationsRemainLocalToMatchingWindows() {
    long start = -8192L;
    OffsetBitSet lhs = createOffsetBitset(start);
    OffsetBitSet rhs = createOffsetBitset(start);

    lhs.set(start);
    lhs.set(start + 2);
    rhs.set(start + 1);
    rhs.set(start + 2);

    OffsetBitSet and = createOffsetBitset(start);
    and.set(start);
    and.set(start + 2);
    and.and(rhs.getBitSet());
    Assertions.assertFalse(and.isSet(start));
    Assertions.assertTrue(and.isSet(start + 2));

    lhs.or(rhs.getBitSet());
    Assertions.assertTrue(lhs.isSet(start));
    Assertions.assertTrue(lhs.isSet(start + 1));
    Assertions.assertTrue(lhs.isSet(start + 2));

    lhs.xor(rhs.getBitSet());
    Assertions.assertTrue(lhs.isSet(start));
    Assertions.assertFalse(lhs.isSet(start + 1));
    Assertions.assertFalse(lhs.isSet(start + 2));

    lhs.set(start + 1);
    lhs.set(start + 2);
    lhs.andNot(rhs.getBitSet());
    Assertions.assertTrue(lhs.isSet(start));
    Assertions.assertFalse(lhs.isSet(start + 1));
    Assertions.assertFalse(lhs.isSet(start + 2));
  }

  @Test
  void iteratorReturnsSignedAbsoluteValuesInOrder() {
    OffsetBitSet bitSet = createOffsetBitset(-8192L);
    List<Long> expected = List.of(-8192L, -4096L, -1L);
    for (long value : expected) {
      bitSet.set(value);
    }

    List<Long> actual = new ArrayList<>();
    Iterator<Long> iterator = bitSet.iterator();
    while (iterator.hasNext()) {
      actual.add(iterator.next());
    }
    Assertions.assertEquals(expected, actual);
  }

  @Test
  void listIteratorTraversesSignedValuesBothDirections() {
    OffsetBitSet bitSet = createOffsetBitset(-8192L);
    List<Long> expected = List.of(-8192L, -4096L, -1L);
    for (long value : expected) {
      bitSet.set(value);
    }

    ListIterator<Long> iterator = bitSet.listIterator();
    List<Long> forward = new ArrayList<>();
    while (iterator.hasNext()) {
      forward.add(iterator.next());
    }
    Assertions.assertEquals(expected, forward);

    List<Long> reverse = new ArrayList<>();
    while (iterator.hasPrevious()) {
      reverse.add(iterator.previous());
    }
    Assertions.assertEquals(List.of(-1L, -4096L, -8192L), reverse);
  }

  @Test
  void compareToDoesNotOverflowAcrossSignedExtrema() {
    OffsetBitSet low = new OffsetBitSet(new BitSetImpl(64), Long.MIN_VALUE);
    OffsetBitSet high = new OffsetBitSet(new BitSetImpl(64), Long.MAX_VALUE);

    Assertions.assertTrue(low.compareTo(high) < 0);
    Assertions.assertTrue(high.compareTo(low) > 0);
  }

  @Test
  void reportsNormalExclusiveEndWithoutUsingItForMembership() {
    OffsetBitSet bitSet = createOffsetBitset(0L);
    Assertions.assertEquals(bitSet.length(), bitSet.getEnd());
  }
}
