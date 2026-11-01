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

import io.mapsmessaging.utilities.collections.bitset.ByteBufferBackedBitMap;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ConcurrentSharedFileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MSG423MappedStorageIntegrationTest {

  private static final int WINDOW_SIZE = 4096;
  private static final long HEADER_BYTES = 16L;
  private static final long RECORD_BYTES = 24L + WINDOW_SIZE / Byte.SIZE;

  @Test
  void mappedFileRestoresSignedBoundsAndIndependentOwners(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("signed-owners.bin");
    long[] ownerOne = {Long.MIN_VALUE, -4097L, -1L, 0L, 4095L, 4097L, Long.MAX_VALUE};
    long[] ownerTwo = {-1L, 4096L, 8192L};

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(7L, factory);
      add(first, ownerOne);
      add(second, ownerTwo);
      Assertions.assertTrue(first.remove(0L));
      Assertions.assertTrue(second.remove(8192L));
      Assertions.assertEquals(ownerOne.length - 1, first.size());
      Assertions.assertEquals(ownerTwo.length - 1, second.size());

      OffsetBitSet mappedWindow = factory.get(-1L).get(0);
      Assertions.assertTrue(((ByteBufferBackedBitMap) mappedWindow.getBitSet()).getBacking().isDirect());
      first.close();
      second.close();
    }
    Assertions.assertTrue(Files.size(file) >= HEADER_BYTES + RECORD_BYTES);

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(7L, factory);
      assertValues(first, Long.MIN_VALUE, -4097L, -1L, 4095L, 4097L, Long.MAX_VALUE);
      assertValues(second, -1L, 4096L);
      Assertions.assertTrue(first.remove(-1L));
      Assertions.assertTrue(second.contains(-1L));
      Assertions.assertEquals(-1L, second.poll());
      Assertions.assertEquals(4096L, second.poll());
      Assertions.assertNull(second.poll());
      first.close();
      second.close();
    }
  }

  @Test
  void releasedMappedRecordIsReusedByAnotherOwnerWithoutLeakingBits(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("reuse.bin");
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(3L, factory);
      add(first, 1L, 4096L);
      long initialLength = Files.size(file);
      Assertions.assertEquals(HEADER_BYTES + RECORD_BYTES * 2, initialLength);

      Assertions.assertTrue(first.remove(1L));
      Assertions.assertEquals(1, factory.getFreeBitSets().size());
      second.offer(8192L);
      Assertions.assertEquals(initialLength, Files.size(file), "Reuse must not append a third record");
      assertValues(first, 4096L);
      assertValues(second, 8192L);
      Assertions.assertEquals(0, factory.getFreeBitSets().size());
      first.close();
      second.close();
    }

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      assertValues(new NaturalOrderedLongQueue(-1L, factory), 4096L);
      assertValues(new NaturalOrderedLongQueue(3L, factory), 8192L);
      Assertions.assertEquals(HEADER_BYTES + RECORD_BYTES * 2, Files.size(file));
    }
  }

  @Test
  void mappedBitwiseBulkOperationsSurviveCloseAndReopen(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("bulk.bin");
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue target = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue source = new NaturalOrderedLongQueue(9L, factory);
      add(target, -4097L, -1L, 0L, 4095L, 4096L, 4097L, 8192L);
      add(source, -1L, 4095L, 4097L, 8193L);
      Assertions.assertTrue(target.addAll(source));
      assertValues(target, -4097L, -1L, 0L, 4095L, 4096L, 4097L, 8192L, 8193L);
      Assertions.assertTrue(target.removeAll(source));
      assertValues(target, -4097L, 0L, 4096L, 8192L);
      Assertions.assertTrue(target.retainAll(List.of(-4097L, 8192L)));
      assertValues(target, -4097L, 8192L);
      assertValues(source, -1L, 4095L, 4097L, 8193L);
      target.close();
      source.close();
    }

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      assertValues(new NaturalOrderedLongQueue(-1L, factory), -4097L, 8192L);
      assertValues(new NaturalOrderedLongQueue(9L, factory), -1L, 4095L, 4097L, 8193L);
    }
  }

  @Test
  void sharedMappedShardsKeepSessionsIsolatedDuringReleaseAndRestart(@TempDir Path directory) throws IOException {
    String base = directory.resolve("shared.bin").toString();
    // -1 and 3 intentionally share shard 3 when there are four shards.
    try (ConcurrentSharedFileBitSetFactoryImpl factory =
             new ConcurrentSharedFileBitSetFactoryImpl(base, 4, WINDOW_SIZE)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(3L, factory);
      add(first, -1L, 4095L, 4097L);
      add(second, -1L, 4096L, 8192L);
      Assertions.assertTrue(first.removeAll(second));
      assertValues(first, 4095L, 4097L);
      assertValues(second, -1L, 4096L, 8192L);
      Assertions.assertTrue(second.remove(4096L));
      assertValues(second, -1L, 8192L);
      first.close();
      second.close();
    }
    try (ConcurrentSharedFileBitSetFactoryImpl factory =
             new ConcurrentSharedFileBitSetFactoryImpl(base, 4, WINDOW_SIZE)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(3L, factory);
      assertValues(first, 4095L, 4097L);
      assertValues(second, -1L, 8192L);
      first.close();
      second.close();
    }
  }

  @Test
  void directBufferAndMappedFileMaintainEquivalentBulkResults(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("direct-and-mapped.bin");
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue mapped = new NaturalOrderedLongQueue(-1L, factory);
      NaturalOrderedLongQueue direct = new NaturalOrderedLongQueue(3L, new ByteBufferBitSetFactoryImpl(WINDOW_SIZE));
      add(mapped, -1L, 0L, 63L, 64L, 4095L, 4096L);
      add(direct, -1L, 63L, 4096L, 4097L);
      Assertions.assertTrue(mapped.addAll(direct));
      assertValues(mapped, -1L, 0L, 63L, 64L, 4095L, 4096L, 4097L);
      Assertions.assertTrue(mapped.removeAll(direct));
      assertValues(mapped, 0L, 64L, 4095L);
      Assertions.assertTrue(direct.retainAll(mapped));
      assertValues(direct);
      mapped.close();
    }
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      assertValues(new NaturalOrderedLongQueue(-1L, factory), 0L, 64L, 4095L);
    }
  }

  @Test
  void rejectedWindowSizeMismatchDoesNotRewriteStoredRecords(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("wrong-window.bin");
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
      queue.offer(4097L);
      queue.close();
    }
    byte[] before = Files.readAllBytes(file);
    Assertions.assertThrows(IOException.class,
        () -> new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE * 2));
    Assertions.assertArrayEquals(before, Files.readAllBytes(file), "Invalid configuration must not rewrite data");
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      assertValues(new NaturalOrderedLongQueue(-1L, factory), 4097L);
    }
  }

  @Test
  void truncatedMappedRecordIsRejectedWithoutFurtherFileChanges(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("truncated.bin");
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
      queue.offer(63L);
      queue.close();
    }
    long truncatedSize = Files.size(file) - 1;
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
      raf.setLength(truncatedSize);
    }
    Assertions.assertThrows(IOException.class,
        () -> new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE));
    Assertions.assertEquals(truncatedSize, Files.size(file), "Reject corrupt layout without truncating it");
  }

  @Test
  void mappedRecordsCanBeClearedClosedAndRecreatedAtSamePath(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("recreate.bin");
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
      add(queue, -1L, 0L, 4096L, 8192L);
      queue.clear();
      Assertions.assertTrue(queue.isEmpty());
      Assertions.assertTrue(factory.getUniqueIds().isEmpty());
    }
    Assertions.assertFalse(Files.exists(file), "A fully released mapped file should be removable");

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      NaturalOrderedLongQueue restored = new NaturalOrderedLongQueue(-1L, factory);
      Assertions.assertTrue(restored.isEmpty());
      restored.offer(4097L);
      assertValues(restored, 4097L);
      restored.close();
    }
  }

  private static void add(NaturalOrderedLongQueue queue, long... ids) {
    for (long id : ids) {
      Assertions.assertTrue(queue.offer(id));
    }
  }

  private static void assertValues(NaturalOrderedLongQueue queue, long... expected) {
    List<Long> values = new ArrayList<>();
    for (long id : expected) {
      values.add(id);
    }
    Assertions.assertEquals(values, new ArrayList<>(queue));
    Assertions.assertEquals(values.size(), queue.size());
  }
}
