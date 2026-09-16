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
import java.util.NoSuchElementException;
import java.util.TreeSet;

class SignedLongDomainTest {

  private static final long[] BOUNDARY_VALUES = {
      Long.MIN_VALUE,
      Long.MIN_VALUE + 1,
      -8193,
      -8192,
      -8191,
      -193,
      -192,
      -191,
      -2,
      -1,
      0,
      1,
      2,
      191,
      192,
      193,
      8191,
      8192,
      8193,
      Long.MAX_VALUE - 1,
      Long.MAX_VALUE
  };

  @Test
  void heapCollectionOrdersCompleteSignedDomain() {
    NaturalOrderedCollection collection = new NaturalOrderedCollection(0, new BitSetFactoryImpl(192));
    TreeSet<Long> expected = new TreeSet<>();

    for (int i = BOUNDARY_VALUES.length - 1; i >= 0; i--) {
      collection.add(BOUNDARY_VALUES[i]);
      expected.add(BOUNDARY_VALUES[i]);
    }

    Assertions.assertEquals(new ArrayList<>(expected), new ArrayList<>(collection));
    for (long value : BOUNDARY_VALUES) {
      Assertions.assertTrue(collection.contains(value), "Missing value " + value);
    }
  }

  @Test
  void directBufferCollectionOrdersCompleteSignedDomain() {
    NaturalOrderedCollection collection = new NaturalOrderedCollection(0, new ByteBufferBitSetFactoryImpl(192));
    TreeSet<Long> expected = new TreeSet<>();

    for (int i = BOUNDARY_VALUES.length - 1; i >= 0; i--) {
      collection.add(BOUNDARY_VALUES[i]);
      expected.add(BOUNDARY_VALUES[i]);
    }

    Assertions.assertEquals(new ArrayList<>(expected), new ArrayList<>(collection));
  }

  @Test
  void queueTreatsMinusOneAsOrdinaryData() {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(0, new BitSetFactoryImpl(192));
    queue.offer(0L);
    queue.offer(-1L);
    queue.offer(-2L);

    Assertions.assertEquals(-2L, queue.remove());
    Assertions.assertEquals(-1L, queue.remove());
    Assertions.assertEquals(0L, queue.remove());
    Assertions.assertNull(queue.poll());
    Assertions.assertThrows(NoSuchElementException.class, queue::remove);
  }

  @Test
  void signedWindowsCoverExtremaWithoutOverlap() {
    BitSetFactory factory = new BitSetFactoryImpl(192);

    long minStart = factory.getStartIndex(Long.MIN_VALUE);
    Assertions.assertEquals(Long.MIN_VALUE, minStart);
    int minLength = factory.getWindowLength(minStart);
    Assertions.assertTrue(minLength > 0 && minLength <= factory.getSize());
    long firstInteriorValue = Long.MIN_VALUE + minLength;
    Assertions.assertNotEquals(minStart, factory.getStartIndex(firstInteriorValue));

    long maxStart = factory.getStartIndex(Long.MAX_VALUE);
    int maxLength = factory.getWindowLength(maxStart);
    Assertions.assertTrue(maxLength > 0 && maxLength <= factory.getSize());
    Assertions.assertEquals(Long.MAX_VALUE, maxStart + maxLength - 1L);
  }

  @Test
  void setOperationsWorkAcrossZeroAndExtrema() {
    NaturalOrderedCollection collection = new NaturalOrderedCollection(0, new BitSetFactoryImpl(192));
    List<Long> values = List.of(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE);

    Assertions.assertTrue(collection.addAll(values));
    Assertions.assertTrue(collection.containsAll(values));
    Assertions.assertTrue(collection.removeAll(List.of(-1L, 1L)));
    Assertions.assertFalse(collection.contains(-1L));
    Assertions.assertFalse(collection.contains(1L));
    Assertions.assertTrue(collection.retainAll(List.of(Long.MIN_VALUE, Long.MAX_VALUE)));
    Assertions.assertEquals(List.of(Long.MIN_VALUE, Long.MAX_VALUE), new ArrayList<>(collection));
  }
}
