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
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

class NaturalOrderedLongQueueTest {

  protected NaturalOrderedLongQueue build(long id, BitSetFactory factory) {
    return new NaturalOrderedLongQueue(id, factory);
  }

  @Test
  void simplePollOfferTest() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(4096);
    NaturalOrderedLongQueue noll = build(0, factory);
    for (long x = 8192; x < 2 * 8192; x++) {
      noll.offer(x);
    }

    for (long x = 0; x < 8192; x++) {
      noll.offer(x);
    }
    int counter = noll.size();
    Assertions.assertNotEquals(counter, 0);
    int start = 0;
    while (!noll.isEmpty()) {
      long ele = noll.element();
      long poll = noll.poll();
      Assertions.assertEquals(ele, poll);
      Assertions.assertEquals(start, poll);
      start++;
    }
  }

  @Test
  void simpleAddRemoveTest() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(4096);
    NaturalOrderedLongQueue noll = build(0, factory);
    for (long x = 8192; x < 2 * 8192; x++) {
      noll.add(x);
    }

    for (long x = 0; x < 8192; x++) {
      noll.offer(x);
    }
    int counter = noll.size();
    Assertions.assertNotEquals(counter, 0);
    int start = 0;
    while (!noll.isEmpty()) {
      Assertions.assertEquals(start, noll.remove());
      start++;
    }
  }

  @Test
  void fileBackedMultiQueueLifecycleTest() throws IOException {
    final int sessionCount = 10;
    final int totalEvents = 2000;
    final int windowSize = 512;
    final String filename = "./multiQueueTest.bit";
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(filename, windowSize);

    try {
      NaturalOrderedLongQueue[] queues = new NaturalOrderedLongQueue[sessionCount];

      for (int i = 0; i < sessionCount; i++) {
        queues[i] = build(i, factory);
      }

      for (long eventId = 0; eventId < totalEvents; eventId++) {
        int sessionIndex = (int) (eventId % sessionCount);
        queues[sessionIndex].offer(eventId);
      }

      factory.close();

      factory = new FileBitSetFactoryImpl(filename, windowSize);
      queues = new NaturalOrderedLongQueue[sessionCount];
      for (int i = 0; i < sessionCount; i++) {
        queues[i] = build(i, factory);
      }

      for (int i = 0; i < sessionCount; i++) {
        long expected = i;
        int count = 0;
        while (!queues[i].isEmpty()) {
          Long actual = queues[i].poll();
          Assertions.assertEquals(expected, actual);
          expected += sessionCount;
          count++;
        }
        Assertions.assertTrue(count > 0);
      }

      int initialFreeSize = factory.getFreeBitSets().size();

      for (int i = 0; i < sessionCount; i += 2) {
        queues[i].clear();
      }

      int increasedFreeSize = factory.getFreeBitSets().size();
      Assertions.assertTrue(increasedFreeSize > initialFreeSize);

      NaturalOrderedLongQueue reusedQueue = build(0, factory);
      Assertions.assertTrue(reusedQueue.isEmpty());
      reusedQueue.offer(99999L);
      Assertions.assertEquals(99999, reusedQueue.poll());
    } finally {
      factory.close();
      Files.deleteIfExists(Paths.get(filename));
    }
  }
}
