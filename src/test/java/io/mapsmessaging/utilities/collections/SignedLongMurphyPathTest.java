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

import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileOffsetBitSet;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class SignedLongMurphyPathTest {

  private static final long FILE_MAGIC = 0x4E4F4C4342495432L;
  private static final int FILE_VERSION = 2;
  private static final int WINDOW_SIZE = 193;
  private static final int LEGACY_WINDOW_SIZE = 192;

  @TempDir
  Path tempDir;

  @Test
  void legacyFreeSentinelIsDiscardedAndMinusOneCanThenBecomeARealId() throws Exception {
    Path file = tempDir.resolve("legacy-free-and-used.bit");
    writeLegacyRecord(file, -1L, 0L, -1);
    writeLegacyRecord(file, 42L, 192L, 5);

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), LEGACY_WINDOW_SIZE)) {
      NaturalOrderedLongQueue legacyQueue = new NaturalOrderedLongQueue(42L, factory);
      Assertions.assertEquals(197L, legacyQueue.peek());
      Assertions.assertTrue(factory.get(-1L).isEmpty());

      NaturalOrderedLongQueue minusOneQueue = new NaturalOrderedLongQueue(-1L, factory);
      Assertions.assertTrue(minusOneQueue.offer(-1L));
      Assertions.assertEquals(-1L, minusOneQueue.peek());
    }

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), LEGACY_WINDOW_SIZE)) {
      NaturalOrderedLongQueue legacyQueue = new NaturalOrderedLongQueue(42L, factory);
      NaturalOrderedLongQueue minusOneQueue = new NaturalOrderedLongQueue(-1L, factory);
      Assertions.assertEquals(197L, legacyQueue.peek());
      Assertions.assertEquals(-1L, minusOneQueue.peek());
    }
  }

  @Test
  void freeRecordCanBeReusedByMinusOneIdAndSurviveRestart() throws Exception {
    Path file = tempDir.resolve("reuse-minus-one.bit");
    long reusedPosition;

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      FileOffsetBitSet first = (FileOffsetBitSet) factory.open(42L, 0L);
      reusedPosition = first.getPosition();
      first.set(1L);
      factory.release(first);

      FileOffsetBitSet reused = (FileOffsetBitSet) factory.open(-1L, -1L);
      Assertions.assertSame(first, reused);
      Assertions.assertEquals(reusedPosition, reused.getPosition());
      Assertions.assertTrue(reused.isAllocated());
      Assertions.assertTrue(factory.getFreeBitSets().isEmpty());
      Assertions.assertTrue(reused.set(-1L));
      Assertions.assertEquals(1, factory.get(-1L).size());
    }

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
      Assertions.assertEquals(-1L, queue.peek());
      Assertions.assertEquals(List.of(-1L), factory.getUniqueIds());
    }
  }

  @Test
  void doubleReleaseDoesNotDuplicateFreeRecords() throws Exception {
    Path file = tempDir.resolve("double-release.bit");

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      OffsetBitSet bitSet = factory.open(Long.MIN_VALUE, Long.MIN_VALUE);
      bitSet.set(Long.MIN_VALUE);

      factory.release(bitSet);
      factory.release(bitSet);

      Assertions.assertEquals(1, factory.getFreeBitSets().size());
      Assertions.assertTrue(factory.get(Long.MIN_VALUE).isEmpty());
    }
  }

  @Test
  void truncatedVersionTwoFileIsRejected() throws Exception {
    Path file = tempDir.resolve("truncated-v2.bit");
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
      writeVersionTwoHeader(raf, WINDOW_SIZE);
      raf.writeLong(1L);
    }

    Assertions.assertThrows(IOException.class,
        () -> new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE));
  }

  @Test
  void invalidVersionTwoRecordStateIsRejected() throws Exception {
    Path file = tempDir.resolve("invalid-state-v2.bit");
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
      writeVersionTwoHeader(raf, WINDOW_SIZE);
      raf.writeLong(99L);
      raf.writeLong(-1L);
      raf.writeLong(0L);
      raf.write(new byte[32]);
    }

    Assertions.assertThrows(IOException.class,
        () -> new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE));
  }

  @Test
  void concurrentSignedOfferAndPollDoesNotLoseOrDuplicateValues() throws Exception {
    ConcurrentNaturalOrderedLongQueue queue = new ConcurrentNaturalOrderedLongQueue(-1L,
        new ByteBufferBitSetFactoryImpl(WINDOW_SIZE));
    TreeSet<Long> expected = new TreeSet<>();

    List<List<Long>> batches = new ArrayList<>();
    batches.add(rangeFromMin(250));
    batches.add(range(-1000L, 250));
    batches.add(range(0L, 250));
    batches.add(rangeDownFromMax(250));
    batches.forEach(expected::addAll);

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<?>> offers = new ArrayList<>();
      for (List<Long> batch : batches) {
        offers.add(executor.submit(() -> batch.forEach(queue::offer)));
      }
      for (Future<?> future : offers) {
        future.get();
      }

      Assertions.assertEquals(expected.size(), queue.size());
      Assertions.assertEquals(new ArrayList<>(expected), new ArrayList<>(queue));

      Set<Long> consumed = ConcurrentHashMap.newKeySet();
      List<Future<?>> polls = new ArrayList<>();
      for (int worker = 0; worker < 4; worker++) {
        polls.add(executor.submit(() -> {
          Long value;
          while ((value = queue.poll()) != null) {
            if (!consumed.add(value)) {
              throw new AssertionError("Duplicate value polled: " + value);
            }
          }
        }));
      }
      for (Future<?> future : polls) {
        future.get();
      }

      Assertions.assertEquals(expected, new TreeSet<>(consumed));
      Assertions.assertTrue(queue.isEmpty());
      Assertions.assertNull(queue.poll());
    } finally {
      executor.shutdownNow();
    }
  }

  private void writeLegacyRecord(Path file, long uniqueId, long offset, int bit) throws IOException {
    int wordCount = LEGACY_WINDOW_SIZE / Long.SIZE;
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
      raf.seek(raf.length());
      raf.writeLong(uniqueId);
      raf.writeLong(offset);
      for (int word = 0; word < wordCount; word++) {
        long value = 0L;
        if (bit >= 0 && word == bit / Long.SIZE) {
          value = 1L << (bit % Long.SIZE);
        }
        raf.writeLong(value);
      }
    }
  }

  private void writeVersionTwoHeader(RandomAccessFile raf, int windowSize) throws IOException {
    raf.writeLong(FILE_MAGIC);
    raf.writeInt(FILE_VERSION);
    raf.writeInt(windowSize);
  }

  private List<Long> rangeFromMin(int count) {
    List<Long> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      values.add(Long.MIN_VALUE + i);
    }
    return values;
  }

  private List<Long> range(long start, int count) {
    List<Long> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      values.add(start + i);
    }
    return values;
  }

  private List<Long> rangeDownFromMax(int count) {
    List<Long> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      values.add(Long.MAX_VALUE - i);
    }
    return values;
  }
}
