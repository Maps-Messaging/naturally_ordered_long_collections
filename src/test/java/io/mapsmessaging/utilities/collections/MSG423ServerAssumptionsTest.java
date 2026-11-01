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

import io.mapsmessaging.utilities.collections.bitset.BitSet;
import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.BitSetImpl;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ConcurrentSharedFileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.TreeSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MSG423ServerAssumptionsTest {

  private static final class CountingFactory extends BitSetFactoryImpl {
    private int opens;
    private int releases;

    CountingFactory(int windowSize) {
      super(windowSize);
    }

    @Override
    public OffsetBitSet open(long uniqueId, long value) {
      opens++;
      return super.open(uniqueId, value);
    }

    @Override
    public void release(OffsetBitSet bitset) {
      releases++;
      super.release(bitset);
    }
  }

  private static final class CountingBitSet extends BitSetImpl {
    private int unions;
    private int differences;
    private int intersections;

    CountingBitSet(int size) {
      super(size);
    }

    @Override
    public void or(BitSet other) {
      unions++;
      super.or(other);
    }

    @Override
    public void andNot(BitSet other) {
      differences++;
      super.andNot(other);
    }

    @Override
    public void and(BitSet other) {
      intersections++;
      super.and(other);
    }
  }

  private static final class BitwiseFactory extends BitSetFactoryImpl {
    private final List<CountingBitSet> bitsets = new ArrayList<>();

    BitwiseFactory(int windowSize) {
      super(windowSize);
    }

    @Override
    public OffsetBitSet open(long uniqueId, long value) {
      long start = getStartIndex(value);
      CountingBitSet bitset = new CountingBitSet(getSize());
      bitsets.add(bitset);
      return new OffsetBitSet(bitset, start, getWindowLength(start));
    }

    int unions() {
      return bitsets.stream().mapToInt(bitset -> bitset.unions).sum();
    }

    int differences() {
      return bitsets.stream().mapToInt(bitset -> bitset.differences).sum();
    }

    int intersections() {
      return bitsets.stream().mapToInt(bitset -> bitset.intersections).sum();
    }
  }

  @Test
  void cachedWindowMovesFromZeroRegionTo4096AndIsReusedBy4098() {
    CountingFactory factory = new CountingFactory(4096);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);

    Assertions.assertTrue(queue.offer(4095L));
    Assertions.assertTrue(queue.remove(4095L));
    assertCachedRegion(queue, 0L);
    OffsetBitSet original = queue.tree.firstEntry().getValue();

    Assertions.assertTrue(queue.offer(4097L));
    Assertions.assertEquals(4096L, queue.tree.firstKey());
    Assertions.assertEquals(2, factory.opens);
    Assertions.assertEquals(1, factory.releases);
    Assertions.assertTrue(queue.remove(4097L));
    assertCachedRegion(queue, 4096L);
    OffsetBitSet current = queue.tree.firstEntry().getValue();
    Assertions.assertNotSame(original, current);

    Assertions.assertTrue(queue.offer(4098L));
    Assertions.assertSame(current, queue.tree.firstEntry().getValue());
    Assertions.assertTrue(queue.remove(4098L));
    assertCachedRegion(queue, 4096L);
    Assertions.assertEquals(2, factory.opens);
    Assertions.assertEquals(1, factory.releases);
  }

  @Test
  void matchingAddAllUsesWindowWiseOrAndPreservesSource() {
    BitwiseFactory destinationFactory = new BitwiseFactory(4096);
    NaturalOrderedLongQueue destination = new NaturalOrderedLongQueue(10L, destinationFactory);
    NaturalOrderedLongQueue source = new NaturalOrderedLongQueue(11L, new BitSetFactoryImpl(4096));
    add(destination, -1L, 4095L, 4097L);
    add(source, 4095L, 4096L, 8192L);

    Assertions.assertTrue(destination.addAll(source));
    Assertions.assertEquals(3, destinationFactory.unions());
    assertValues(destination, -1L, 4095L, 4096L, 4097L, 8192L);
    assertValues(source, 4095L, 4096L, 8192L);
    Assertions.assertFalse(destination.addAll(source));
    Assertions.assertEquals(6, destinationFactory.unions());
  }

  @Test
  void matchingRemoveAllUsesWindowWiseAndNotAndPreservesSource() {
    BitwiseFactory destinationFactory = new BitwiseFactory(4096);
    NaturalOrderedLongQueue destination = new NaturalOrderedLongQueue(10L, destinationFactory);
    NaturalOrderedLongQueue source = new NaturalOrderedLongQueue(11L, new BitSetFactoryImpl(4096));
    add(destination, -1L, 4095L, 4096L, 4097L, 8192L);
    add(source, 4095L, 4097L, 8192L);

    Assertions.assertTrue(destination.removeAll(source));
    Assertions.assertEquals(3, destinationFactory.differences());
    assertValues(destination, -1L, 4096L);
    assertValues(source, 4095L, 4097L, 8192L);
    Assertions.assertFalse(destination.removeAll(source));
  }

  @Test
  void matchingRetainAllUsesWindowWiseAndAndPreservesSource() {
    BitwiseFactory destinationFactory = new BitwiseFactory(4096);
    NaturalOrderedLongQueue destination = new NaturalOrderedLongQueue(10L, destinationFactory);
    NaturalOrderedLongQueue source = new NaturalOrderedLongQueue(11L, new BitSetFactoryImpl(4096));
    add(destination, -1L, 4095L, 4096L, 4097L, 8192L);
    add(source, 4095L, 4097L);

    Assertions.assertTrue(destination.retainAll(source));
    Assertions.assertEquals(2, destinationFactory.intersections());
    assertValues(destination, 4095L, 4097L);
    assertValues(source, 4095L, 4097L);
    Assertions.assertFalse(destination.retainAll(source));
  }

  @Test
  void differentWindowSizesUseElementWiseFallbackCorrectly() {
    NaturalOrderedLongQueue destination = new NaturalOrderedLongQueue(10L, new BitSetFactoryImpl(4096));
    NaturalOrderedLongQueue source = new NaturalOrderedLongQueue(11L, new BitSetFactoryImpl(64));
    add(destination, -1L, 63L, 4096L);
    add(source, -1L, 64L, 4097L);

    Assertions.assertTrue(destination.addAll(source));
    assertValues(destination, -1L, 63L, 64L, 4096L, 4097L);
    Assertions.assertTrue(destination.removeAll(source));
    assertValues(destination, 63L, 4096L);
    Assertions.assertTrue(destination.retainAll(source));
    assertValues(destination);
    assertValues(source, -1L, 64L, 4097L);
  }

  @Test
  void crossBackendBitwiseOperationsMatchReferenceValues() {
    NaturalOrderedLongQueue heap = new NaturalOrderedLongQueue(10L, new BitSetFactoryImpl(64));
    NaturalOrderedLongQueue direct = new NaturalOrderedLongQueue(11L, new ByteBufferBitSetFactoryImpl(64));
    add(heap, -65L, -1L, 63L, 64L, 128L);
    add(direct, -1L, 0L, 64L, 65L);

    Assertions.assertTrue(heap.addAll(direct));
    assertValues(heap, -65L, -1L, 0L, 63L, 64L, 65L, 128L);
    Assertions.assertTrue(heap.removeAll(direct));
    assertValues(heap, -65L, 63L, 128L);
    Assertions.assertTrue(direct.retainAll(heap));
    assertValues(direct);
    assertValues(heap, -65L, 63L, 128L);
  }

  @Test
  void emptyCachedSourceDoesNotCreatePhantomWindowOnAddAll() {
    CountingFactory destinationFactory = new CountingFactory(4096);
    NaturalOrderedLongQueue destination = new NaturalOrderedLongQueue(10L, destinationFactory);
    NaturalOrderedLongQueue source = new NaturalOrderedLongQueue(11L, new BitSetFactoryImpl(4096));
    source.offer(4097L);
    source.remove(4097L);
    assertCachedRegion(source, 4096L);

    Assertions.assertFalse(destination.addAll(source), "An empty source must not count as inserted data");
    Assertions.assertTrue(destination.tree.isEmpty(), "No destination bitmap is necessary without any values");
    Assertions.assertEquals(0, destinationFactory.opens);
    Assertions.assertTrue(destination.isEmpty());
  }

  @Test
  void emptyCachedSourceDoesNotAddEmptyWindowToPopulatedDestination() {
    CountingFactory destinationFactory = new CountingFactory(4096);
    NaturalOrderedLongQueue destination = new NaturalOrderedLongQueue(10L, destinationFactory);
    NaturalOrderedLongQueue source = new NaturalOrderedLongQueue(11L, new BitSetFactoryImpl(4096));
    destination.offer(1L);
    source.offer(8192L);
    source.remove(8192L);

    Assertions.assertFalse(destination.addAll(source));
    Assertions.assertEquals(1, destinationFactory.opens);
    Assertions.assertEquals(1, destination.tree.size());
    assertValues(destination, 1L);
  }

  @Test
  void subscriptionInterestFilteringRetainsOnlyUninterestedEvents() {
    NaturalOrderedLongQueue candidates = new NaturalOrderedLongQueue(1L, new BitSetFactoryImpl(4096));
    NaturalOrderedLongQueue firstInterest = new NaturalOrderedLongQueue(2L, new BitSetFactoryImpl(4096));
    NaturalOrderedLongQueue secondInterest = new NaturalOrderedLongQueue(3L, new BitSetFactoryImpl(4096));
    add(candidates, -1L, 0L, 4095L, 4096L, 4097L, 8193L);
    add(firstInterest, -1L, 4096L);
    add(secondInterest, 4097L);
    // Mirrors DestinationSubscriptionManager.scanForInterest().
    Assertions.assertTrue(candidates.removeAll(firstInterest));
    Assertions.assertTrue(candidates.removeAll(secondInterest));
    assertValues(candidates, 0L, 4095L, 8193L);
    assertValues(firstInterest, -1L, 4096L);
    assertValues(secondInterest, 4097L);

    secondInterest.poll();
    Assertions.assertFalse(candidates.removeAll(secondInterest));
    assertValues(candidates, 0L, 4095L, 8193L);
  }

  @Test
  void serverStateCopyCombinesAtRestAndInflightAcrossPriorities() {
    @SuppressWarnings("unchecked")
    Queue<Long>[] priorityQueues = new Queue[11];
    for (int priority = 0; priority < priorityQueues.length; priority++) {
      priorityQueues[priority] =
          new NaturalOrderedLongQueue(priority, new BitSetFactoryImpl(4096));
    }
    PriorityQueue<Long> atRest = new PriorityQueue<>(priorityQueues, null);
    PriorityCollection<Long> inFlight = new PriorityCollection<>(11, null);
    Assertions.assertTrue(atRest.add(4095L, 9));
    Assertions.assertTrue(atRest.add(4097L, 10));
    Assertions.assertTrue(inFlight.add(-1L, 7));
    Assertions.assertTrue(inFlight.add(8192L, 3));

    NaturalOrderedLongQueue snapshot = new NaturalOrderedLongQueue(99L, new BitSetFactoryImpl(4096));
    atRest.flatten(snapshot);
    Assertions.assertTrue(snapshot.addAll(inFlight));
    assertValues(snapshot, -1L, 4095L, 4097L, 8192L);
    Assertions.assertEquals(2, atRest.size());
    Assertions.assertEquals(2, inFlight.size());

    Assertions.assertTrue(atRest.remove(4097L));
    Assertions.assertTrue(inFlight.add(4097L, 10));
    NaturalOrderedLongQueue nextSnapshot = new NaturalOrderedLongQueue(100L, new BitSetFactoryImpl(4096));
    atRest.flatten(nextSnapshot);
    Assertions.assertTrue(nextSnapshot.addAll(inFlight));
    assertValues(nextSnapshot, -1L, 4095L, 4097L, 8192L);
  }

  @Test
  void persistentSubscriptionFilteringSurvivesRestart(@TempDir Path tempDir) throws IOException {
    String base = tempDir.resolve("subscriptions.bin").toString();
    try (ConcurrentSharedFileBitSetFactoryImpl factory =
             new ConcurrentSharedFileBitSetFactoryImpl(base, 4, 4096)) {
      NaturalOrderedLongQueue candidates = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue interest = new NaturalOrderedLongQueue(18L, factory);
      add(candidates, 4095L, 4096L, 4097L, 8192L);
      add(interest, 4096L, 8192L);
      Assertions.assertTrue(candidates.removeAll(interest));
      assertValues(candidates, 4095L, 4097L);
      candidates.close();
      interest.close();
    }
    try (ConcurrentSharedFileBitSetFactoryImpl factory =
             new ConcurrentSharedFileBitSetFactoryImpl(base, 4, 4096)) {
      NaturalOrderedLongQueue candidates = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue interest = new NaturalOrderedLongQueue(18L, factory);
      assertValues(candidates, 4095L, 4097L);
      assertValues(interest, 4096L, 8192L);
      Assertions.assertFalse(candidates.removeAll(interest));
      assertValues(candidates, 4095L, 4097L);
      candidates.close();
      interest.close();
    }
  }

  @Test
  void storageStyleKeyAggregationKeepsUniqueSignedOrder() {
    NaturalOrderedLongList keys = new NaturalOrderedLongList();
    Assertions.assertTrue(keys.addAll(List.of(4097L, 2L, -1L)));
    Assertions.assertTrue(keys.addAll(List.of(4096L, 4097L, 0L)));
    Assertions.assertEquals(new ArrayList<>(new TreeSet<>(List.of(-1L, 0L, 2L, 4096L, 4097L))),
        new ArrayList<>(keys));
    Assertions.assertFalse(keys.addAll(List.of(4097L, 2L, -1L)));
  }

  private static void add(NaturalOrderedLongQueue queue, long... ids) {
    for (long id : ids) {
      Assertions.assertTrue(queue.offer(id), "Duplicate test fixture ID " + id);
    }
  }

  private static void assertValues(NaturalOrderedCollection collection, long... ids) {
    List<Long> expected = new ArrayList<>();
    for (long id : ids) {
      expected.add(id);
    }
    Assertions.assertEquals(expected, new ArrayList<>(collection));
    Assertions.assertEquals(expected.size(), collection.size());
  }

  private static void assertCachedRegion(NaturalOrderedLongQueue queue, long expectedStart) {
    Assertions.assertTrue(queue.isEmpty());
    Assertions.assertEquals(1, queue.tree.size());
    OffsetBitSet cached = queue.tree.firstEntry().getValue();
    Assertions.assertEquals(expectedStart, cached.getStart());
    Assertions.assertTrue(cached.isEmpty());
  }
}
