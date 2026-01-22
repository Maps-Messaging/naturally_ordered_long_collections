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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentNaturalOrderedLongQueueTest {

  private ConcurrentNaturalOrderedLongQueue queue;

  @BeforeEach
  void setUp() {
    BitSetFactory factory = new BitSetFactoryImpl(8192); // Use FileBitSetFactoryImpl if needed
    queue = new ConcurrentNaturalOrderedLongQueue(new Random().nextLong(), factory);
  }

  @AfterEach
  void tearDown() {
    queue.close();
  }

  @Test
  void testOfferAndPoll() {
    assertTrue(queue.offer(10L));
    assertEquals(10L, queue.poll());
    assertNull(queue.poll());
  }

  @Test
  void testAddAndRemove() {
    assertTrue(queue.add(20L));
    assertTrue(queue.contains(20L));
    assertTrue(queue.remove(20L));
    assertFalse(queue.contains(20L));
  }

  @Test
  void testAddAllAndClear() {
    List<Long> values = Arrays.asList(1L, 2L, 3L);
    assertTrue(queue.addAll(values));
    assertEquals(3, queue.size());
    queue.clear();
    assertTrue(queue.isEmpty());
  }

  @Test
  void testRetainAll() {
    queue.addAll(Arrays.asList(1L, 2L, 3L));
    assertTrue(queue.retainAll(Collections.singleton(2L)));
    assertEquals(1, queue.size());
    assertTrue(queue.contains(2L));
  }

  @Test
  void testRemoveAll() {
    queue.addAll(Arrays.asList(1L, 2L, 3L));
    assertTrue(queue.removeAll(Arrays.asList(2L, 3L)));
    assertEquals(1, queue.size());
    assertTrue(queue.contains(1L));
  }

  @Test
  void testRemoveIf() {
    queue.addAll(Arrays.asList(10L, 15L, 20L));
    assertTrue(queue.removeIf(x -> x > 10));
    assertEquals(1, queue.size());
    assertTrue(queue.contains(10L));
  }

  @Test
  void testToArray() {
    queue.addAll(Arrays.asList(5L, 10L));
    Object[] array = queue.toArray();
    assertEquals(2, array.length);
    assertTrue(Arrays.asList(array).contains(5L));
  }

  @Test
  void testForEach() {
    queue.addAll(Arrays.asList(1L, 2L));
    AtomicLong sum = new AtomicLong(0);
    queue.forEach(sum::addAndGet);
    assertEquals(3L, sum.get());
  }

  @Test
  void testIterator() {
    queue.addAll(Arrays.asList(100L, 200L));
    Iterator<Long> it = queue.iterator();
    Set<Long> result = new HashSet<>();
    it.forEachRemaining(result::add);
    assertTrue(result.containsAll(Arrays.asList(100L, 200L)));
  }

  @Test
  void testGetUniqueId() {
    long id = queue.getUniqueId();
    assertNotEquals(0, id);
  }

  @Test
  void testElementAndPeek() {
    queue.offer(77L);
    assertEquals(77L, queue.element());
    assertEquals(77L, queue.peek());
  }

  @Test
  void testRemoveThrowsException() {
    assertThrows(NoSuchElementException.class, () -> queue.remove());
  }

  @Test
  void testElementThrowsException() {
    assertNull(queue.element());
  }
}