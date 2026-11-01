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
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MSG423ConcurrentCursorIteratorTest {

  @Test
  void additionsAheadAreVisibleButAdditionsBehindAreNot() {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.offer(0L);
    queue.offer(64L);
    Iterator<Long> iterator = queue.iterator();

    Assertions.assertEquals(0L, iterator.next());
    queue.offer(1L);
    queue.offer(128L);
    queue.offer(-1L);
    Assertions.assertEquals(1L, iterator.next());
    Assertions.assertEquals(64L, iterator.next());
    Assertions.assertEquals(128L, iterator.next());
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  void removalAndReleaseOfUpcomingWindowsDoesNotInvalidateTraversal() {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.addAll(List.of(-1L, 63L, 64L, 128L));
    Iterator<Long> iterator = queue.iterator();
    Assertions.assertEquals(-1L, iterator.next());

    Assertions.assertTrue(queue.remove(63L));
    Assertions.assertTrue(queue.remove(-1L));
    Assertions.assertTrue(queue.remove(64L));
    Assertions.assertTrue(iterator.hasNext());
    Assertions.assertEquals(128L, iterator.next());
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  void disappearanceBetweenHasNextAndNextIsHandledByNoSuchElement() {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.offer(64L);
    Iterator<Long> iterator = queue.iterator();
    Assertions.assertTrue(iterator.hasNext());
    Assertions.assertTrue(queue.remove(64L));
    Assertions.assertFalse(iterator.hasNext());
    Assertions.assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  void iteratorRemoveWorksAcrossReleasedWindowsAndWithMinusOne() {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.addAll(List.of(-1L, 63L, 64L));
    Iterator<Long> iterator = queue.iterator();
    Assertions.assertEquals(-1L, iterator.next());
    iterator.remove();
    Assertions.assertThrows(IllegalStateException.class, iterator::remove);
    Assertions.assertTrue(queue.remove(63L));
    Assertions.assertEquals(64L, iterator.next());
    iterator.remove();
    Assertions.assertTrue(queue.isEmpty());
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  void extremeSignedIdsDoNotOverflowTheCursor() {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.addAll(List.of(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE));
    Iterator<Long> iterator = queue.iterator();
    Assertions.assertEquals(Long.MIN_VALUE, iterator.next());
    Assertions.assertEquals(-1L, iterator.next());
    Assertions.assertEquals(0L, iterator.next());
    Assertions.assertEquals(Long.MAX_VALUE, iterator.next());
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  void bulkRemovalsAndAdditionsRetainCursorSemantics() {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.addAll(List.of(1L, 64L, 128L, 192L));
    Iterator<Long> iterator = queue.iterator();
    Assertions.assertEquals(1L, iterator.next());
    Assertions.assertTrue(queue.removeAll(List.of(64L, 128L)));
    Assertions.assertTrue(queue.addAll(List.of(65L, 256L)));
    Assertions.assertEquals(65L, iterator.next());
    Assertions.assertTrue(queue.retainAll(List.of(1L, 256L)));
    Assertions.assertEquals(256L, iterator.next());
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  void mutationOnAnotherThreadCannotExposeReleasedBacking() throws InterruptedException {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.addAll(List.of(63L, 64L, 128L));
    Iterator<Long> iterator = queue.iterator();
    Assertions.assertEquals(63L, iterator.next());

    Thread writer = new Thread(() -> {
      queue.remove(63L);
      queue.remove(64L);
    }, "msg423-cursor-writer");
    writer.start();
    writer.join(5000L);
    Assertions.assertFalse(writer.isAlive(), "Concurrent writer must complete");

    Assertions.assertEquals(128L, iterator.next());
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  void closingQueueInvalidatesOlderIteratorsButNotNewOnes() {
    ConcurrentNaturalOrderedLongQueue queue = queue();
    queue.offer(1L);
    Iterator<Long> previous = queue.iterator();
    queue.close();
    Assertions.assertThrows(IllegalStateException.class, previous::hasNext);
    Assertions.assertThrows(IllegalStateException.class, previous::next);
    Assertions.assertThrows(IllegalStateException.class, previous::remove);
    Assertions.assertFalse(queue.iterator().hasNext());
  }

  private static ConcurrentNaturalOrderedLongQueue queue() {
    return new ConcurrentNaturalOrderedLongQueue(17L, new BitSetFactoryImpl(64));
  }
}
