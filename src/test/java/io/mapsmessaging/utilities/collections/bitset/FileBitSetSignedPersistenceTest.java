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

package io.mapsmessaging.utilities.collections.bitset;

import io.mapsmessaging.utilities.collections.NaturalOrderedLongQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class FileBitSetSignedPersistenceTest {

  private static final int WINDOW_SIZE = 192;

  @Test
  void persistsMinusOneAsOrdinaryUniqueId() throws IOException {
    Path file = Files.createTempFile("signed-long", ".bit");
    Files.deleteIfExists(file);

    try {
      try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
        queue.offer(Long.MIN_VALUE);
        queue.offer(-1L);
        queue.offer(0L);
        queue.offer(Long.MAX_VALUE);
      }

      try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
        Assertions.assertEquals(List.of(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE), queue.stream().toList());
      }
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void freeRecordsAreExposedWithoutReservedId() throws IOException {
    Path file = Files.createTempFile("signed-long-free", ".bit");
    Files.deleteIfExists(file);

    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
      OffsetBitSet bitSet = factory.open(-1L, -1L);
      bitSet.set(-1L);
      factory.release(bitSet);

      Assertions.assertTrue(factory.get(-1L).isEmpty());
      Assertions.assertEquals(1, factory.getFreeBitSets().size());
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void migratesLegacyPositiveDomainRecord() throws IOException {
    Path file = Files.createTempFile("signed-long-legacy", ".bit");
    writeLegacyRecord(file, 42L, 0L, 5);

    try {
      try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(42L, factory);
        Assertions.assertEquals(5L, queue.peek());
      }

      try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(42L, factory);
        Assertions.assertEquals(5L, queue.peek());
      }
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private void writeLegacyRecord(Path file, long uniqueId, long offset, int bit) throws IOException {
    int bytes = WINDOW_SIZE / Byte.SIZE;
    byte[] payload = new byte[bytes];
    int byteIndex = bit / Byte.SIZE;
    int bitIndex = bit % Byte.SIZE;
    payload[byteIndex] = (byte) (1 << bitIndex);

    try (RandomAccessFile randomAccessFile = new RandomAccessFile(file.toFile(), "rw")) {
      randomAccessFile.setLength(0);
      randomAccessFile.writeLong(uniqueId);
      randomAccessFile.writeLong(offset);
      randomAccessFile.write(payload);
    }
  }
}
