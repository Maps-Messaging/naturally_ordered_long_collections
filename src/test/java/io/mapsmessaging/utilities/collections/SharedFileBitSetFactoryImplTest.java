
/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2025 ] MapsMessaging B.V.
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

import io.mapsmessaging.utilities.collections.bitset.SharedFileBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

class SharedFileBitSetFactoryImplTest {

  private static final String BASE_FILENAME = "./shardedQueueTest.bit";
  private static final int SHARD_COUNT = 4;
  private static final int WINDOW_SIZE = 128;
  private static final int SESSION_COUNT = 20;
  private static final int TOTAL_EVENTS = 2000;
  @Test
  void testShardAllocationAndPersistence() throws Exception {
    try {
      // Phase 1: Allocate and persist data
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        NaturalOrderedLongQueue[] queues = new NaturalOrderedLongQueue[SESSION_COUNT];
        for (int i = 0; i < SESSION_COUNT; i++) {
          queues[i] = new NaturalOrderedLongQueue(i, factory);
        }
        for (long id = 0; id < TOTAL_EVENTS; id++) {
          int sessionIndex = (int) (id % SESSION_COUNT);
          queues[sessionIndex].offer(id);
        }
      }

      // Phase 2: Reload and validate
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        for (int i = 0; i < SESSION_COUNT; i++) {
          NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(i, factory);
          long expected = i;
          int count = 0;
          while (!queue.isEmpty()) {
            Assertions.assertEquals(expected, queue.poll());
            expected += SESSION_COUNT;
            count++;
          }
          Assertions.assertTrue(count > 0);
        }
      }
    } finally {
      closeFiles();

    }
  }

  @Test
  void testReloadWithUnknownSessionIds() throws IOException {
    List<Long> sessionIds = List.of(
        8359L, 5557L, 1106L, 3615L, 7924L,
        6574L, 5552L, 3547L, 4527L, 6514L,
        2674L, 2519L, 7224L, 2584L, 6881L,
        6635L, 5333L, 1711L, 8527L, 9785L
    );
    try {
      // Phase 1: Save data
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        for (long id : sessionIds) {
          NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue((int)id, factory);
          queue.offer(id * 10L);
          queue.offer(id * 10L + 1);
        }
      }

      // Phase 2: Reload and discover IDs
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        List<Long> discoveredIds = factory.getUniqueIds().stream().sorted().collect(Collectors.toList());
        List<Long> expectedIds = sessionIds.stream().sorted().collect(Collectors.toList());
        Assertions.assertEquals(expectedIds, discoveredIds);
        for (long id : discoveredIds) {
          NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue((int)id, factory);
          Assertions.assertEquals(id * 10L, queue.poll());
          Assertions.assertEquals(id * 10L + 1, queue.poll());
          Assertions.assertTrue(queue.isEmpty());
        }
      }
    } finally {
      for (int i = 0; i < SHARD_COUNT; i++) {
        Files.deleteIfExists(Paths.get(BASE_FILENAME + "_" + i));
      }
    }
  }



  @Test
  void testShardReloadAndIntegrity() throws Exception {
    try {
      // Phase 1: Write data
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        for (int i = 0; i < SESSION_COUNT; i++) {
          NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(i, factory);
          for (long eventId = i; eventId < TOTAL_EVENTS; eventId += SESSION_COUNT) {
            queue.offer(eventId);
          }
        }
      }

      // Phase 2: Reload and verify
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        for (int i = 0; i < SESSION_COUNT; i++) {
          NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(i, factory);
          long expected = i;
          int count = 0;
          while (!queue.isEmpty()) {
            Assertions.assertEquals(expected, queue.poll());
            expected += SESSION_COUNT;
            count++;
          }
          Assertions.assertTrue(count > 0);
        }
      }
    } finally {
      closeFiles();

    }
  }

  @Test
  void testShardReuseAndFreeListBehavior() throws Exception {
    try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
      NaturalOrderedLongQueue[] queues = new NaturalOrderedLongQueue[SESSION_COUNT];
      for (int i = 0; i < SESSION_COUNT; i++) {
        queues[i] = new NaturalOrderedLongQueue(i, factory);
        queues[i].offer((long) i);
      }

      int initialFree = factory.get(-1).size();
      for (int i = 0; i < SESSION_COUNT; i += 2) {
        queues[i].clear();
      }
      int increasedFree = factory.get(-1).size();
      Assertions.assertTrue(increasedFree > initialFree);

      NaturalOrderedLongQueue reused = new NaturalOrderedLongQueue(0, factory);
      Assertions.assertTrue(reused.isEmpty());
      reused.offer(9999L);
      Assertions.assertEquals(9999L, reused.poll());
    } finally {
      closeFiles();
    }
  }

  private void closeFiles() throws IOException, InterruptedException {
    for (int i = 0; i < SHARD_COUNT; i++) {
      boolean deleted = false;
      int count = 0;
      while(!deleted) {
        try {
          System.gc();
          Thread.sleep(100);
          count++;
          Files.deleteIfExists(Paths.get(BASE_FILENAME + "_" + i));
          deleted = true;
        } catch (Exception e) {
          if(count > 4){
            throw e;
          }
          System.gc();
          Thread.sleep(100);

        }
      }
    }
  }

  @Test
  void testGetAllEventIdsWithInterest() throws IOException {
    try {
      // Insert events across sessions
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        NaturalOrderedLongQueue[] queues = new NaturalOrderedLongQueue[SESSION_COUNT];
        for (int i = 0; i < SESSION_COUNT; i++) {
          queues[i] = new NaturalOrderedLongQueue(i, factory);
        }

        for (long id = 0; id < TOTAL_EVENTS; id++) {
          int sessionIndex = (int) (id % SESSION_COUNT);
          queues[sessionIndex].offer(id);
        }
      }

      // Reload and validate getAllEventIdsWithInterest
      try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
        List<Long> allEvents = factory.getAllEventIdsWithInterest();
        Assertions.assertEquals(TOTAL_EVENTS, allEvents.size());

        // Optional: Check all expected IDs exist
        for (long id = 0; id < TOTAL_EVENTS; id++) {
          Assertions.assertTrue(allEvents.contains(id));
        }
      }
    } finally {
      for (int i = 0; i < SHARD_COUNT; i++) {
        Files.deleteIfExists(Paths.get(BASE_FILENAME + "_" + i));
      }
    }
  }
  @Test
  void testEmptyFactoryReturnsNoEvents() throws IOException, InterruptedException {
    try (SharedFileBitSetFactoryImpl factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE)) {
      Assertions.assertTrue(factory.getAllEventIdsWithInterest().isEmpty());
    } finally {
      closeFiles();
    }
  }

}
