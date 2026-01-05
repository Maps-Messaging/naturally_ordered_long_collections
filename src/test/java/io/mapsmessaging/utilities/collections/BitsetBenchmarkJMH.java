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


import io.mapsmessaging.utilities.collections.bitset.SharedFileBitSetFactoryImpl;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class BitsetBenchmarkJMH {

  private static final String BASE_FILENAME = "./benchmark_bitset";
  private static final int SHARD_COUNT = 4;
  private static final int WINDOW_SIZE = 128;
  private static final int PRIORITY_LEVELS = 16;
  private static final int EVENT_COUNT = 1000;
  private static final int SESSION_BATCH = 5;

  private SharedFileBitSetFactoryImpl factory;

  @Setup(Level.Iteration)
  public void setup() throws IOException {
    factory = new SharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE);
  }

  @TearDown(Level.Iteration)
  public void tearDown() throws IOException, InterruptedException {
    factory.close();
    for (int i = 0; i < SHARD_COUNT; i++) {
      java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(BASE_FILENAME + "_" + i));
    }
  }

  @Benchmark
  public void simulateSessionLifecycle() {
    List<NaturalOrderedLongQueue> queues = new ArrayList<>();

    for (int sessionIndex = 0; sessionIndex < SESSION_BATCH; sessionIndex++) {
      long baseId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

      for (int direction = 0; direction < 2; direction++) {
        for (int priority = 0; priority < PRIORITY_LEVELS; priority++) {
          long uniqueId = (baseId << 5) | (direction << 4) | priority;
          NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue((int)uniqueId, factory);
          queues.add(queue);

          for (long i = 0; i < EVENT_COUNT; i++) {
            queue.offer(i);
          }
        }
      }
    }

    for (NaturalOrderedLongQueue queue : queues) {
      while (!queue.isEmpty()) {
        queue.poll();
      }
      queue.close();
    }
  }
}
