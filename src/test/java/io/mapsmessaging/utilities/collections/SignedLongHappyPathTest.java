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
import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

class SignedLongHappyPathTest {

  private static final int NON_ALIGNED_WINDOW_SIZE = 193;

  @Test
  void queueOrdersSignedValuesAcrossCompleteDomainWithNonAlignedWindows() {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L,
        new ByteBufferBitSetFactoryImpl(NON_ALIGNED_WINDOW_SIZE));

    long[] input = {
        Long.MAX_VALUE,
        194L,
        -1L,
        Long.MIN_VALUE,
        0L,
        -194L,
        193L,
        Long.MAX_VALUE - 1,
        1L,
        -193L,
        Long.MIN_VALUE + 1,
        192L,
        -192L,
        2L,
        -2L
    };

    TreeSet<Long> expected = new TreeSet<>();
    for (long value : input) {
      Assertions.assertTrue(queue.offer(value), "Expected first offer to add " + value);
      expected.add(value);
    }

    for (long value : expected) {
      Assertions.assertEquals(value, queue.poll());
    }
    Assertions.assertNull(queue.poll());
    Assertions.assertTrue(queue.isEmpty());
  }

  @Test
  void duplicateSignedValuesRemainUniqueAcrossWindowBoundaries() {
    NaturalOrderedCollection collection = new NaturalOrderedCollection(-1L,
        new BitSetFactoryImpl(NON_ALIGNED_WINDOW_SIZE));

    List<Long> values = List.of(
        Long.MIN_VALUE,
        -194L,
        -193L,
        -1L,
        0L,
        192L,
        193L,
        Long.MAX_VALUE);

    for (long value : values) {
      Assertions.assertTrue(collection.add(value));
      Assertions.assertFalse(collection.add(value), "Duplicate should not change collection for " + value);
    }

    Assertions.assertEquals(values.size(), collection.size());
    Assertions.assertEquals(new ArrayList<>(new TreeSet<>(values)), new ArrayList<>(collection));
  }

  @Test
  void collectionSetOperationsDoNotExposePhysicalPaddingBits() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(NON_ALIGNED_WINDOW_SIZE);
    NaturalOrderedCollection left = new NaturalOrderedCollection(1L, factory);
    NaturalOrderedCollection right = new NaturalOrderedCollection(2L, factory);

    left.addAll(List.of(-194L, -193L, -1L, 0L, 192L, 193L, 194L));
    right.addAll(List.of(-193L, -1L, 193L, 194L, 386L));

    Assertions.assertTrue(left.addAll(right));
    Assertions.assertEquals(
        List.of(-194L, -193L, -1L, 0L, 192L, 193L, 194L, 386L),
        new ArrayList<>(left));

    Assertions.assertTrue(left.retainAll(List.of(-193L, -1L, 193L, 386L)));
    Assertions.assertEquals(List.of(-193L, -1L, 193L, 386L), new ArrayList<>(left));
    Assertions.assertEquals(4, left.size());
  }

  @Test
  void signedWindowMathsContainsEveryProbeAcrossWindowSizeMatrix() {
    int[] windowSizes = {1, 2, 63, 64, 65, 127, 128, 129, 192, 193, 1000, 8192};
    long[] probes = {
        Long.MIN_VALUE,
        Long.MIN_VALUE + 1,
        -8193L,
        -8192L,
        -1001L,
        -1000L,
        -194L,
        -193L,
        -192L,
        -2L,
        -1L,
        0L,
        1L,
        2L,
        192L,
        193L,
        194L,
        999L,
        1000L,
        1001L,
        8191L,
        8192L,
        8193L,
        Long.MAX_VALUE - 1,
        Long.MAX_VALUE
    };

    for (int windowSize : windowSizes) {
      BitSetFactory factory = new BitSetFactoryImpl(windowSize);
      Assertions.assertEquals(0L, factory.getStartIndex(0L));
      Assertions.assertEquals(-((long) windowSize), factory.getStartIndex(-1L));

      for (long value : probes) {
        long start = factory.getStartIndex(value);
        int length = factory.getWindowLength(start);
        Assertions.assertTrue(length > 0 && length <= windowSize,
            "Invalid logical length for window size " + windowSize + " at " + start);
        Assertions.assertTrue(value >= start,
            "Window starts after value for size " + windowSize + ": " + value + " -> " + start);
        Assertions.assertTrue(value - start < length,
            "Value outside logical window for size " + windowSize + ": " + value + " -> " + start + "/" + length);
      }
    }
  }
}
