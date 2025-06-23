/*
 *  Copyright [ 2020 - 2024 ] [Matthew Buckton]
 *  Copyright [ 2024 - 2025 ] [Maps Messaging B.V.]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.mapsmessaging.utilities.collections;

import io.mapsmessaging.utilities.collections.bitset.SharedFileBitSetFactoryImpl;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BitSetFileStorageBenchmarkJMH {

  private static final int SHARD_COUNT = 4;
  private static final int WINDOW_SIZE = 128;
  private static final int PRIORITY_LEVELS = 16;
  private static final int SESSION_MULTIPLIER = 2;
  private static final int EVENTS_PER_PRIORITY = 1000;

  private SharedFileBitSetFactoryImpl factory;
  private NaturalOrderedLongQueue[] queues;

  @Setup(Level.Trial)
  public void setup() throws IOException {
    Path temp = Files.createTempFile("bitset-benchmark", ".bit");
    factory = new SharedFileBitSetFactoryImpl(temp.toString(), SHARD_COUNT, WINDOW_SIZE);
    queues = new NaturalOrderedLongQueue[SESSION_MULTIPLIER * PRIORITY_LEVELS];

    long baseSessionId = uuidToUniqueId(UUID.randomUUID());
    for (int i = 0; i < queues.length; i++) {
      queues[i] = new NaturalOrderedLongQueue((int)baseSessionId + i, factory);
    }
  }

  @TearDown(Level.Trial)
  public void teardown() throws IOException {
    for (NaturalOrderedLongQueue queue : queues) {
      queue.close();
    }
    factory.close();
  }

  @Benchmark
  public void writeAndClearEvents() {
    for (int i = 0; i < queues.length; i++) {
      for (long j = 0; j < EVENTS_PER_PRIORITY; j++) {
        queues[i].offer(j);
      }
    }
    for (int i = 0; i < queues.length; i++) {
      while (!queues[i].isEmpty()) {
        queues[i].poll();
      }
    }
  }

  private long uuidToUniqueId(UUID uuid) {
    return uuid.getMostSignificantBits() & 0x7FFFFFFFFFFFFFFFL; // positive 63-bit
  }
}
