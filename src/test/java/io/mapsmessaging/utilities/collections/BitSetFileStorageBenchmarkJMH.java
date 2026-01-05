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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@Fork(1)
@Threads(16) // Adjust to simulate real concurrent load
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
public class BitSetFileStorageBenchmarkJMH {

  private static final int SHARD_COUNT = 8;
  private static final int WINDOW_SIZE = 128;
  private static final int PRIORITY_LEVELS = 16;
  private static final int SESSION_MULTIPLIER = 8;
  private static final int EVENTS_PER_PRIORITY = 10000;

  private SharedFileBitSetFactoryImpl factory;
  private NaturalOrderedLongQueue[] queues;
  private LongAdder adder = new LongAdder();
  private long iterationStartNanos;

  @Setup(Level.Trial)
  public void setup() throws IOException {
    Path temp = Files.createTempFile("bitset-benchmark", ".bit");
    factory = new SharedFileBitSetFactoryImpl(temp.toString(), SHARD_COUNT, WINDOW_SIZE);
    queues = new NaturalOrderedLongQueue[SESSION_MULTIPLIER * PRIORITY_LEVELS];

    int baseSessionId = 1;
    for (int i = 0; i < queues.length; i++) {
      queues[i] = new NaturalOrderedLongQueue(baseSessionId++, factory);
    }

  }

  @TearDown(Level.Trial)
  public void teardown() throws IOException {
    for (NaturalOrderedLongQueue queue : queues) {
      queue.clear();
      queue.close();
    }
    factory.close();
  }

  @Setup(Level.Iteration)
  public void startTiming() {
    iterationStartNanos = System.nanoTime();
  }


  @Benchmark
  public void writeAndClearEvents() {
    for (NaturalOrderedLongQueue queue : queues) {
      for (long j = 0; j < EVENTS_PER_PRIORITY; j++) {
        synchronized (queue) {
          queue.offer(j);
          adder.increment();
        }
      }
    }
    for (NaturalOrderedLongQueue queue : queues) {
      while (!(test(queue))){
        adder.increment();
      }
    }
  }

  @TearDown(Level.Iteration)
  public void reportStats() {
    long durationNanos = System.nanoTime() - iterationStartNanos;
    long bitOps = adder.sumThenReset();
    double seconds = durationNanos / 1_000_000_000.0;
    double bitOpsPerSecond = seconds > 0 ? bitOps / seconds : 0;

    System.out.printf("Total bit operations this iteration: %,d%n", bitOps);
    System.out.printf("Bit operations per second: %,.2f%n", bitOpsPerSecond);
  }

  private boolean test(NaturalOrderedLongQueue queue){
    synchronized (queue) {
      queue.poll();
      return queue.isEmpty();
    }
  }

}
