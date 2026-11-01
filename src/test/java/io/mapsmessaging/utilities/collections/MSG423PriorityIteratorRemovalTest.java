/*
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
import java.util.Iterator;
import java.util.Queue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MSG423PriorityIteratorRemovalTest {

  @Test
  void movingMessageToAnotherPriorityBeforeIteratorRemovalDoesNotChangeCount() {
    PriorityCollection<Long> collection = concurrentPriorities();
    collection.add(42L, 1);
    Iterator<Long> iterator = collection.iterator();
    Assertions.assertEquals(42L, iterator.next());

    Assertions.assertTrue(collection.add(42L, 2));
    iterator.remove();

    Assertions.assertFalse(collection.isEmpty());
    Assertions.assertTrue(collection.contains(42L));
    Assertions.assertEquals(1, collection.size());
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
  }

  @Test
  void removingMessageExternallyBeforeIteratorRemovalDoesNotDoubleDecrement() {
    PriorityCollection<Long> collection = concurrentPriorities();
    collection.add(42L, 1);
    Iterator<Long> iterator = collection.iterator();
    Assertions.assertEquals(42L, iterator.next());
    Assertions.assertTrue(collection.remove(42L));
    iterator.remove();

    Assertions.assertTrue(collection.isEmpty());
    collection.add(77L, 1);
    Assertions.assertFalse(collection.isEmpty());
    Assertions.assertTrue(collection.contains(77L));
    Assertions.assertEquals(1, collection.size());
  }

  @Test
  void removeUsesLastReturnedPriorityEvenAfterHasNextAdvances() {
    PriorityCollection<Long> collection = concurrentPriorities();
    collection.add(42L, 1);
    collection.add(99L, 0);
    Iterator<Long> iterator = collection.iterator();
    Assertions.assertEquals(42L, iterator.next());
    Assertions.assertTrue(iterator.hasNext());
    iterator.remove();

    Assertions.assertEquals(99L, iterator.next());
    iterator.remove();
    Assertions.assertTrue(collection.isEmpty());
    Assertions.assertEquals(0, collection.size());
  }

  @Test
  void listBackedPriorityIteratorStillSupportsItsOwnRemovalAndContinuation() {
    PriorityCollection<Long> collection = new PriorityCollection<>(3, null);
    collection.add(42L, 1);
    collection.add(43L, 1);
    Iterator<Long> iterator = collection.iterator();
    Assertions.assertEquals(42L, iterator.next());
    iterator.remove();
    Assertions.assertEquals(43L, iterator.next());
    iterator.remove();
    Assertions.assertFalse(iterator.hasNext());
    Assertions.assertTrue(collection.isEmpty());
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
  }

  private static PriorityCollection<Long> concurrentPriorities() {
    @SuppressWarnings("unchecked")
    Queue<Long>[] queues = new Queue[3];
    for (int priority = 0; priority < queues.length; priority++) {
      queues[priority] = new ConcurrentNaturalOrderedLongQueue(priority, new BitSetFactoryImpl(64));
    }
    return new PriorityCollection<>(queues, null);
  }
}
