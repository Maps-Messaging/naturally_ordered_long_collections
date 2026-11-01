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
import io.mapsmessaging.utilities.collections.bitset.BitSetImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MSG423CollectionContractTest {

  private static final class TrackingFactory extends BitSetFactoryImpl {
    private int openings;
    private int releases;

    TrackingFactory() {
      this(64);
    }

    TrackingFactory(int windowSize) {
      super(windowSize);
    }

    @Override
    public OffsetBitSet open(long uniqueId, long id) {
      openings++;
      return super.open(uniqueId, id);
    }

    @Override
    public void release(OffsetBitSet bitset) {
      releases++;
      super.release(bitset);
    }
  }

  @Test
  void drainedQueueRetainsLastWindowForReuse() {
    TrackingFactory factory = new TrackingFactory();
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(1L, factory);

    Assertions.assertTrue(queue.offer(-1L));
    Assertions.assertEquals(-1L, queue.poll());
    Assertions.assertNull(queue.poll());
    Assertions.assertEquals(1, factory.openings);
    Assertions.assertEquals(0, factory.releases);

    Assertions.assertTrue(queue.offer(-2L));
    Assertions.assertEquals(-2L, queue.poll());
    Assertions.assertEquals(1, factory.openings);
    Assertions.assertEquals(0, factory.releases);
  }

  @Test
  void queueReleasesEmptyWindowOnlyWhenAnotherExists() {
    TrackingFactory factory = new TrackingFactory();
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(1L, factory);

    queue.offer(1L);
    queue.offer(65L);
    Assertions.assertEquals(1L, queue.poll());
    Assertions.assertEquals(1, factory.releases);
    Assertions.assertEquals(65L, queue.poll());
    Assertions.assertEquals(1, factory.releases);
    Assertions.assertTrue(queue.isEmpty());
    queue.offer(66L);
    Assertions.assertEquals(2, factory.openings);
  }

  @Test
  void explicitRemovalRetainsSoleEmptyWindow() {
    TrackingFactory factory = new TrackingFactory();
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(1L, factory);

    queue.offer(1L);
    Assertions.assertTrue(queue.remove(1L));
    Assertions.assertTrue(queue.isEmpty());
    queue.offer(2L);
    Assertions.assertEquals(1, factory.openings);
    Assertions.assertEquals(0, factory.releases);
  }

  @Test
  void monotonicAddRemoveRecyclesOneWindowAtEachBoundary() {
    TrackingFactory factory = new TrackingFactory(4096);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(1L, factory);

    for (long id = 0; id <= 1_000_000L; id++) {
      Assertions.assertTrue(queue.offer(id));
      Assertions.assertTrue(queue.remove(id));
    }

    Assertions.assertTrue(queue.isEmpty());
    Assertions.assertEquals(245, factory.openings, "One window opening per 4096 IDs, not per event");
    Assertions.assertEquals(244, factory.releases, "Only old empty windows should be released");
    Assertions.assertTrue(queue.offer(1_000_001L));
    Assertions.assertTrue(queue.remove(1_000_001L));
    Assertions.assertEquals(245, factory.openings, "The final empty window must remain reusable");
  }

  @Test
  void repeatedAddRemoveInSameWindowDoesNotReallocate() {
    TrackingFactory factory = new TrackingFactory(4096);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(1L, factory);

    for (int iteration = 0; iteration < 10_000; iteration++) {
      Assertions.assertTrue(queue.offer(0L));
      Assertions.assertTrue(queue.remove(0L));
      Assertions.assertTrue(queue.offer(1L));
      Assertions.assertTrue(queue.remove(1L));
    }

    Assertions.assertEquals(1, factory.openings);
    Assertions.assertEquals(0, factory.releases);
    Assertions.assertTrue(queue.isEmpty());
  }

  @Test
  void typedArraysRespectCollectionContract() {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue();
    queue.offer(-1L);
    queue.offer(64L);

    Assertions.assertArrayEquals(new Long[] {-1L, 64L}, queue.toArray(new Long[0]));
    Long[] larger = {11L, 12L, 13L, 14L};
    Assertions.assertSame(larger, queue.toArray(larger));
    Assertions.assertArrayEquals(new Long[] {-1L, 64L, null, 14L}, larger);
  }

  @Test
  void collectionIteratorThrowsWhenExhausted() {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue();
    queue.offer(7L);

    Iterator<Long> iterator = queue.iterator();
    Assertions.assertEquals(7L, iterator.next());
    Assertions.assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  void bitsetIteratorsEnforceExhaustionAndRemovalState() {
    BitSetImpl bitset = new BitSetImpl(64);
    Iterator<Integer> iterator = bitset.iterator();
    Assertions.assertThrows(NoSuchElementException.class, iterator::next);
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);

    bitset.set(1);
    iterator = bitset.iterator();
    Assertions.assertEquals(1, iterator.next());
    iterator.remove();
    Assertions.assertFalse(bitset.isSet(1));
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
    Assertions.assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  void listIteratorTraversesBothDirectionsAndThrowsOnExhaustion() {
    BitSetImpl bitset = new BitSetImpl(64);
    bitset.set(1);
    bitset.set(7);
    ListIterator<Integer> iterator = bitset.listIterator();

    Assertions.assertFalse(iterator.hasPrevious());
    Assertions.assertThrows(NoSuchElementException.class, iterator::previous);
    Assertions.assertEquals(1, iterator.next());
    Assertions.assertEquals(1, iterator.previous());
    Assertions.assertEquals(1, iterator.next());
    Assertions.assertEquals(7, iterator.next());
    Assertions.assertThrows(NoSuchElementException.class, iterator::next);
    Assertions.assertEquals(7, iterator.previous());
    Assertions.assertEquals(1, iterator.previous());
    Assertions.assertThrows(NoSuchElementException.class, iterator::previous);
  }

  @Test
  void priorityIteratorRemovalUpdatesImmediatelyObservableState() {
    PriorityCollection<Long> collection = new PriorityCollection<>(3, null);
    collection.add(42L, 2);
    Iterator<Long> iterator = collection.iterator();

    Assertions.assertEquals(42L, iterator.next());
    iterator.remove();
    Assertions.assertTrue(collection.isEmpty());
    Assertions.assertFalse(collection.contains(42L));
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
  }

  @Test
  void priorityBulkOperationsReturnWhetherContentsChanged() {
    PriorityCollection<Long> collection = new PriorityCollection<>(3, value -> 0);
    Assertions.assertFalse(collection.addAll(List.of()));
    Assertions.assertTrue(collection.addAll(List.of(1L, 2L)));
    Assertions.assertFalse(collection.addAll(List.of(1L, 2L)));
    Assertions.assertFalse(collection.removeAll(List.of(99L)));
    Assertions.assertTrue(collection.removeAll(List.of(1L, 99L)));
    Assertions.assertEquals(1, collection.size());

    PriorityCollection<Long> copy = new PriorityCollection<>(3, null);
    Assertions.assertTrue(copy.addAll(collection));
    Assertions.assertFalse(copy.addAll(collection));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new PriorityCollection<Long>(2, null).addAll(collection));
  }

  @Test
  void removingAllFromSelfClearsPriorityCollection() {
    PriorityCollection<Long> collection = new PriorityCollection<>(3, null);
    collection.add(1L, 1);
    collection.add(2L, 2);
    Assertions.assertTrue(collection.removeAll(collection));
    Assertions.assertTrue(collection.isEmpty());
    Assertions.assertEquals(0, collection.size());
    Assertions.assertFalse(collection.removeAll(collection));
  }

  @Test
  void priorityTypedArrayDoesNotTruncateOrLeaveStaleTerminator() {
    PriorityCollection<Long> collection = new PriorityCollection<>(3, null);
    collection.add(1L, 1);
    collection.add(2L, 2);
    Assertions.assertArrayEquals(new Long[] {2L, 1L}, collection.toArray(new Long[0]));

    Long[] larger = {0L, 0L, 99L, 100L};
    Assertions.assertSame(larger, collection.toArray(larger));
    Assertions.assertArrayEquals(new Long[] {2L, 1L, null, 100L}, larger);
  }
}
