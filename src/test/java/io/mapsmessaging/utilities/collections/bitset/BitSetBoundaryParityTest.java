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

import java.nio.ByteBuffer;

class BitSetBoundaryParityTest {

  private static final int CAPACITY = 192;

  @Test
  void heapAndMappedUseExclusiveUpperBoundary() {
    for (BitSet bitSet : bitSets(CAPACITY)) {
      Assertions.assertTrue(bitSet.set(CAPACITY - 1));
      Assertions.assertTrue(bitSet.isSet(CAPACITY - 1));
      Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitSet.set(CAPACITY));
      Assertions.assertThrows(IndexOutOfBoundsException.class, () -> bitSet.set(-1));
    }
  }

  @Test
  void fullRangeFlipIncludesEveryLegalBit() {
    for (BitSet bitSet : bitSets(CAPACITY)) {
      bitSet.flip(0, CAPACITY);
      Assertions.assertEquals(CAPACITY, bitSet.cardinality());
      for (int bit = 0; bit < CAPACITY; bit++) {
        Assertions.assertTrue(bitSet.isSet(bit));
      }
    }
  }

  @Test
  void crossWordFlipMatchesHeapSemantics() {
    BitSet heap = new BitSetImpl(CAPACITY);
    BitSet mapped = mapped(CAPACITY);

    heap.flip(13, 151);
    mapped.flip(13, 151);

    assertSameBits(heap, mapped);
  }

  @Test
  void nextSetBitAndClearPreservesEarlierBits() {
    for (BitSet bitSet : bitSets(CAPACITY)) {
      bitSet.set(1);
      bitSet.set(70);
      Assertions.assertEquals(70, bitSet.nextSetBitAndClear(64));
      Assertions.assertTrue(bitSet.isSet(1));
      Assertions.assertFalse(bitSet.isSet(70));
    }
  }

  @Test
  void previousSearchClampsToLastLegalBit() {
    for (BitSet bitSet : bitSets(CAPACITY)) {
      bitSet.set(CAPACITY - 1);
      Assertions.assertEquals(CAPACITY - 1, bitSet.previousSetBit(Integer.MAX_VALUE));
    }
  }

  @Test
  void mappedBufferOffsetDoesNotChangeLogicalIndexing() {
    ByteBuffer buffer = ByteBuffer.allocate(8 + (CAPACITY / Byte.SIZE));
    BitSet mapped = new ByteBufferBackedBitMap(buffer, 8);
    mapped.set(0);
    mapped.set(64);
    mapped.set(CAPACITY - 1);

    Assertions.assertEquals(0, mapped.nextSetBit(0));
    Assertions.assertEquals(64, mapped.nextSetBit(1));
    Assertions.assertEquals(CAPACITY - 1, mapped.previousSetBit(CAPACITY - 1));
  }

  @Test
  void bitwiseOperationsMatchAcrossDifferentBackendSizes() {
    BitSet heap = new BitSetImpl(CAPACITY);
    BitSet mappedShort = mapped(64);
    heap.set(1);
    heap.set(130);
    mappedShort.set(1);

    heap.and(mappedShort);
    Assertions.assertTrue(heap.isSet(1));
    Assertions.assertFalse(heap.isSet(130));

    BitSet mapped = mapped(CAPACITY);
    BitSet heapShort = new BitSetImpl(64);
    mapped.set(1);
    mapped.set(130);
    heapShort.set(1);

    mapped.and(heapShort);
    Assertions.assertTrue(mapped.isSet(1));
    Assertions.assertFalse(mapped.isSet(130));
  }

  private BitSet[] bitSets(int capacity) {
    return new BitSet[]{new BitSetImpl(capacity), mapped(capacity)};
  }

  private BitSet mapped(int capacity) {
    return new ByteBufferBackedBitMap(ByteBuffer.allocate(capacity / Byte.SIZE), 0);
  }

  private void assertSameBits(BitSet expected, BitSet actual) {
    Assertions.assertEquals(expected.length(), actual.length());
    Assertions.assertEquals(expected.cardinality(), actual.cardinality());
    for (int bit = 0; bit < expected.length(); bit++) {
      Assertions.assertEquals(expected.isSet(bit), actual.isSet(bit), "Mismatch at bit " + bit);
    }
  }
}
