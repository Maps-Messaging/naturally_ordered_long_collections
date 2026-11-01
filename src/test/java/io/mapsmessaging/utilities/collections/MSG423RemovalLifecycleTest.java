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

import io.mapsmessaging.utilities.collections.bitset.BitSetFactory;
import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import io.mapsmessaging.utilities.collections.bitset.SharedFileBitSetFactoryImpl;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MSG423RemovalLifecycleTest {

  private static final class CountingFactory extends BitSetFactoryImpl {
    private int openings;
    private int releases;

    CountingFactory(int windowSize) {
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
  void removingAllFromSelfRetainsOneReusableWindow() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    for (long id : new long[]{-129L, -1L, 0L, 63L, 64L, 1000L}) {
      queue.offer(id);
    }

    Assertions.assertEquals(5, queue.tree.size());
    Assertions.assertTrue(queue.removeAll(queue));
    assertOneEmptyWindow(queue);
    Assertions.assertEquals(4, factory.releases);
    Assertions.assertFalse(queue.removeAll(queue));
    Assertions.assertEquals(4, factory.releases);

    Assertions.assertTrue(queue.offer(1001L));
    Assertions.assertTrue(queue.remove(1001L));
    Assertions.assertEquals(5, factory.openings);
    Assertions.assertEquals(4, factory.releases);
    assertOneEmptyWindow(queue);
  }

  @Test
  void matchingRemoveAllDoesNotReportChangeForEmptyCache() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    NaturalOrderedLongQueue other = new NaturalOrderedLongQueue(18L, new BitSetFactoryImpl(64));
    queue.offer(63L);
    queue.remove(63L);
    other.offer(63L);

    Assertions.assertFalse(queue.removeAll(other));
    assertOneEmptyWindow(queue);
    Assertions.assertEquals(0, factory.releases);
  }

  @Test
  void matchingRemovalPreservesSourceAndLastWindow() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    NaturalOrderedLongQueue other = new NaturalOrderedLongQueue(18L, new BitSetFactoryImpl(64));
    for (long id : new long[]{-65L, -1L, 0L, 64L, 128L}) {
      queue.offer(id);
      other.offer(id);
    }

    Assertions.assertTrue(queue.removeAll(other));
    assertOneEmptyWindow(queue);
    Assertions.assertEquals(4, factory.releases);
    Assertions.assertEquals(5, other.size());
    Assertions.assertFalse(queue.removeAll(other));
    Assertions.assertEquals(4, factory.releases);
  }

  @Test
  void retainingSelfNeverDropsCachedOrPopulatedWindows() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    queue.offer(-1L);
    queue.offer(64L);
    Assertions.assertFalse(queue.retainAll(queue));
    Assertions.assertEquals(List.of(-1L, 64L), new ArrayList<>(queue));
    Assertions.assertEquals(0, factory.releases);

    queue.poll();
    queue.poll();
    assertOneEmptyWindow(queue);
    int releases = factory.releases;
    Assertions.assertFalse(queue.retainAll(queue));
    Assertions.assertEquals(releases, factory.releases);
    assertOneEmptyWindow(queue);
  }

  @Test
  void matchingRetainAllClearsValuesAndKeepsOneWindow() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    NaturalOrderedLongQueue other = new NaturalOrderedLongQueue(18L, new BitSetFactoryImpl(64));
    for (long id : new long[]{-65L, -1L, 0L, 64L, 128L}) {
      queue.offer(id);
    }

    Assertions.assertTrue(queue.retainAll(other));
    assertOneEmptyWindow(queue);
    Assertions.assertEquals(4, factory.releases);
    Assertions.assertFalse(queue.retainAll(other));
    assertOneEmptyWindow(queue);
  }

  @Test
  void plainRetainAllAndRemoveIfReleaseSurplusWindows() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    for (long id : new long[]{-65L, -1L, 0L, 64L, 128L}) {
      queue.offer(id);
    }
    Assertions.assertTrue(queue.retainAll(List.of(-1L, 128L)));
    Assertions.assertEquals(List.of(-1L, 128L), new ArrayList<>(queue));
    Assertions.assertEquals(2, queue.tree.size());
    Assertions.assertTrue(queue.removeIf(value -> true));
    assertOneEmptyWindow(queue);
    Assertions.assertEquals(4, factory.releases);
    Assertions.assertFalse(queue.removeIf(value -> true));
  }

  @Test
  void iteratorRemovalTargetsLastReturnedAcrossWindowBoundary() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    queue.offer(63L);
    queue.offer(64L);
    Iterator<Long> iterator = queue.iterator();

    Assertions.assertEquals(63L, iterator.next());
    Assertions.assertTrue(iterator.hasNext());
    iterator.remove();
    Assertions.assertEquals(64L, iterator.next());
    Assertions.assertFalse(queue.contains(63L));
    Assertions.assertEquals(1, factory.releases);
    iterator.remove();
    assertOneEmptyWindow(queue);
    Assertions.assertEquals(1, factory.releases);
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
  }

  @Test
  void listIteratorRemovesWindowsConsistently() {
    CountingFactory factory = new CountingFactory(64);
    NaturalOrderedLongList list = new NaturalOrderedLongList(17L, factory);
    list.add(63L);
    list.add(64L);
    ListIterator<Long> iterator = list.listIterator();

    Assertions.assertEquals(63L, iterator.next());
    Assertions.assertTrue(iterator.hasNext());
    iterator.remove();
    Assertions.assertEquals(64L, iterator.next());
    iterator.remove();
    Assertions.assertTrue(list.isEmpty());
    Assertions.assertEquals(1, list.tree.size());
    Assertions.assertEquals(1, factory.releases);
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
  }

  @Test
  void persistentSharedFactoryRestoresOnlyExpectedWindows(@TempDir Path tempDir) throws IOException {
    String base = tempDir.resolve("interest.bin").toString();
    long sessionId = -1L;
    try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(base, 4, 64)) {
      NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(sessionId, factory);
      NaturalOrderedLongQueue peer = new NaturalOrderedLongQueue(2L, factory);
      for (long id : new long[]{-65L, -1L, 0L, 64L, 128L}) {
        queue.offer(id);
        peer.offer(id);
      }
      Assertions.assertTrue(queue.removeAll(peer));
      assertOneEmptyWindow(queue);
      Assertions.assertEquals(1, factory.get(sessionId).size());
      Assertions.assertEquals(5, peer.size());
      queue.close();
      peer.close();
    }
    try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(base, 4, 64)) {
      NaturalOrderedLongQueue restored = new NaturalOrderedLongQueue(sessionId, factory);
      Assertions.assertTrue(restored.isEmpty());
      Assertions.assertEquals(1, restored.tree.size());
      restored.offer(129L);
      Assertions.assertEquals(129L, restored.poll());
      Assertions.assertEquals(1, restored.tree.size());
      restored.close();
    }
  }

  @Test
  void bulkMillionEventRemovalKeepsSingleReusableWindow() {
    CountingFactory factory = new CountingFactory(4096);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(17L, factory);
    for (long id = 0; id <= 1_000_000L; id++) {
      queue.offer(id);
    }
    Assertions.assertEquals(245, queue.tree.size());
    Assertions.assertTrue(queue.removeAll(queue));
    assertOneEmptyWindow(queue);
    Assertions.assertEquals(245, factory.openings);
    Assertions.assertEquals(244, factory.releases);
    queue.offer(1_000_001L);
    Assertions.assertEquals(245, factory.openings);
    Assertions.assertEquals(1_000_001L, queue.poll());
  }

  @Test
  void seededServerStyleMutationSequenceMatchesTreeSet() {
    runModel(new BitSetFactoryImpl(64));
    runModel(new ByteBufferBitSetFactoryImpl(64));
  }

  private void runModel(BitSetFactory factory) {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
    NaturalOrderedLongQueue other = new NaturalOrderedLongQueue(2L, factory);
    TreeSet<Long> expected = new TreeSet<>();
    Random random = new Random(423L);
    long[] boundaries = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -129L, -65L, -64L,
        -1L, 0L, 1L, 63L, 64L, 65L, 128L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
    boolean opened = false;

    for (int step = 0; step < 2000; step++) {
      long id = random.nextBoolean() ? boundaries[random.nextInt(boundaries.length)] : random.nextInt(8192) - 4096L;
      int operation = random.nextInt(6);
      switch (operation) {
        case 0 -> {
          Assertions.assertEquals(expected.add(id), queue.offer(id));
          opened = true;
        }
        case 1 -> Assertions.assertEquals(expected.remove(id), queue.remove(id));
        case 2, 3 -> {
          other.clear();
          TreeSet<Long> values = new TreeSet<>();
          for (int i = 0; i < 4; i++) {
            long candidate = boundaries[random.nextInt(boundaries.length)];
            other.offer(candidate);
            values.add(candidate);
          }
          if (operation == 2) {
            Assertions.assertEquals(expected.removeAll(values), queue.removeAll(other));
          } else {
            Assertions.assertEquals(expected.retainAll(values), queue.retainAll(other));
          }
        }
        case 4 -> Assertions.assertEquals(expected.removeIf(value -> (value & 1L) == 0L),
            queue.removeIf(value -> (value & 1L) == 0L));
        case 5 -> Assertions.assertEquals(expected.pollFirst(), queue.poll());
        default -> throw new AssertionError("Unexpected operation");
      }
      Assertions.assertEquals(new ArrayList<>(expected), new ArrayList<>(queue), "operation " + step);
      Assertions.assertEquals(expected.size(), queue.size(), "operation " + step);
      if (expected.isEmpty()) {
        Assertions.assertTrue(queue.tree.size() <= 1, "At most one empty cached window: step " + step);
        if (opened) {
          assertOneEmptyWindow(queue);
        }
      } else {
        for (OffsetBitSet bitset : queue.tree.values()) {
          Assertions.assertFalse(bitset.isEmpty(), "No redundant empty windows: step " + step);
        }
      }
    }
  }

  private static void assertOneEmptyWindow(NaturalOrderedLongQueue queue) {
    Assertions.assertTrue(queue.isEmpty());
    Assertions.assertEquals(0, queue.size());
    Assertions.assertEquals(1, queue.tree.size(), "Keep exactly one empty window for reuse");
    OffsetBitSet cached = queue.tree.firstEntry().getValue();
    Assertions.assertTrue(cached.isActive());
    Assertions.assertTrue(cached.isEmpty());
  }
}
