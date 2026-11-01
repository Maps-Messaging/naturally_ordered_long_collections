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
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SignedLongReferenceModelAuditTest {

  private static final long SEED = 0x423L;
  private static final int[] WINDOW_SIZES = {1, 3, 7, 63, 64, 65, 257, 8192};
  private static final long[] BOUNDARIES = {
      Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MIN_VALUE + 2,
      -8193, -8192, -8191, -65, -64, -63, -2, -1, 0, 1, 2,
      63, 64, 65, 8191, 8192, Long.MAX_VALUE - 2,
      Long.MAX_VALUE - 1, Long.MAX_VALUE
  };

  @Test
  void heapQueueMatchesReferenceAcrossSignedWindows() {
    for (int windowSize : WINDOW_SIZES) {
      compareToReference(new BitSetFactoryImpl(windowSize), windowSize);
    }
  }

  @Test
  void directBufferQueueMatchesReferenceAcrossSignedWindows() {
    for (int windowSize : WINDOW_SIZES) {
      compareToReference(new ByteBufferBitSetFactoryImpl(windowSize), windowSize);
    }
  }

  private void compareToReference(BitSetFactory factory, int windowSize) {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
    TreeSet<Long> expected = new TreeSet<>();
    Random random = new Random(SEED + windowSize);

    for (long value : BOUNDARIES) {
      Assertions.assertEquals(expected.add(value), queue.offer(value));
    }
    verify(queue, expected, windowSize, -1);

    for (int step = 0; step < 500; step++) {
      long value = random.nextInt(3) == 0
          ? BOUNDARIES[random.nextInt(BOUNDARIES.length)]
          : random.nextLong();
      switch (random.nextInt(6)) {
        case 0, 1 -> Assertions.assertEquals(expected.add(value), queue.offer(value),
            diagnostic(windowSize, step));
        case 2 -> Assertions.assertEquals(expected.remove(value), queue.remove(Long.valueOf(value)),
            diagnostic(windowSize, step));
        case 3 -> Assertions.assertEquals(expected.pollFirst(), queue.poll(), diagnostic(windowSize, step));
        case 4 -> Assertions.assertEquals(expected.contains(value), queue.contains(value),
            diagnostic(windowSize, step));
        case 5 -> {
          Assertions.assertEquals(expected.isEmpty() ? null : expected.first(), queue.peek(),
              diagnostic(windowSize, step));
          if (expected.isEmpty()) {
            Assertions.assertThrows(java.util.NoSuchElementException.class, queue::element);
          } else {
            Assertions.assertEquals(expected.first(), queue.element(), diagnostic(windowSize, step));
          }
        }
        default -> throw new AssertionError("Unexpected operation");
      }
      verify(queue, expected, windowSize, step);
    }
  }

  private void verify(NaturalOrderedLongQueue queue, TreeSet<Long> expected, int windowSize, int step) {
    String context = diagnostic(windowSize, step);
    Assertions.assertEquals(expected.size(), queue.size(), context);
    Assertions.assertEquals(expected.isEmpty(), queue.isEmpty(), context);
    Assertions.assertEquals(new ArrayList<>(expected), new ArrayList<>(queue), context);
    Assertions.assertArrayEquals(expected.toArray(), queue.toArray(), context);
    Assertions.assertEquals(expected.isEmpty() ? null : expected.first(), queue.peek(), context);
  }

  private String diagnostic(int windowSize, int step) {
    return "seed=" + SEED + ", windowSize=" + windowSize + ", step=" + step;
  }
}
