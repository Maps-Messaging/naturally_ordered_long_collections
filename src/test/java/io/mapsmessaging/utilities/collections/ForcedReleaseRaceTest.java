/*
 *  Copyright [ 2020 - 2024 ] [Matthew Buckton]
 *  Copyright [ 2024 - 2026 ] [Maps Messaging B.V.]
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

import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ForcedReleaseRaceTest {

  static final class ReleasingFactory extends BitSetFactoryImpl {
    ReleasingFactory(int size) {
      super(size);
    }

    @Override
    public void release(OffsetBitSet bitset) {
      bitset.releaseBitSet();
    }
  }

  @Test
  void iteratorEscape_plusPollRelease_canTriggerIllegalState() throws Exception {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(1L, new ReleasingFactory(8192));

    for (long i = 0; i < 300_000; i++) {
      queue.offer(i);
    }

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Future<?> poller = executor.submit(() -> {
      await(start);
      try {
        while (failure.get() == null) {
          queue.poll();
          queue.peek();
          queue.isEmpty();
        }
      } catch (Throwable t) {
        failure.compareAndSet(null, t);
      }
    });

    Future<?> iteratorUser = executor.submit(() -> {
      await(start);
      try {
        while (failure.get() == null) {
          Iterator<Long> it = queue.iterator();
          int steps = 0;
          while (it.hasNext() && steps < 256) {
            it.next();
            steps++;
          }
        }
      } catch (Throwable t) {
        failure.compareAndSet(null, t);
      }
    });

    start.countDown();

    Throwable observed = null;
    for (int i = 0; i < 800; i++) { // ~8s
      observed = failure.get();
      if (observed != null) {
        break;
      }
      Thread.sleep(10);
    }

    poller.cancel(true);
    iteratorUser.cancel(true);
    executor.shutdownNow();
    executor.awaitTermination(2, TimeUnit.SECONDS);

    assertNotNull(observed, "Did not reproduce in the time window. Increase duration/iterations.");
    assertInstanceOf(IllegalStateException.class, observed);
    assertTrue(observed.getMessage().contains("released"));
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
