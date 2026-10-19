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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

public class FileOffsetBitSetTest {

  @TempDir
  Path tempDir;

  @Test
  void fileOffsetBitSetKeepsPositionAndShard() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64, 7);

    OffsetBitSet opened = factory.open(1L, 0L);

    Assertions.assertInstanceOf(FileOffsetBitSet.class, opened);

    FileOffsetBitSet bitset = (FileOffsetBitSet) opened;

    Assertions.assertEquals(0L, bitset.getPosition());
    Assertions.assertEquals(0L, bitset.getStart());
    Assertions.assertEquals(64L, bitset.getEnd());
    Assertions.assertEquals(1L, bitset.getBitSet().getUniqueId());

    factory.close();
  }

  @Test
  void fileOffsetBitSetResetUpdatesStartEndAndUniqueId() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64, 3);

    OffsetBitSet opened = factory.open(1L, 0L);
    FileOffsetBitSet bitset = (FileOffsetBitSet) opened;

    bitset.set(1L);
    Assertions.assertTrue(bitset.isSet(1L));

    bitset.reset(128L, 99L);

    Assertions.assertEquals(128L, bitset.getStart());
    Assertions.assertEquals(192L, bitset.getEnd());
    Assertions.assertEquals(99L, bitset.getBitSet().getUniqueId());
    Assertions.assertTrue(bitset.isEmpty());

    bitset.set(128L);
    Assertions.assertTrue(bitset.isSet(128L));

    factory.close();
  }

  @Test
  void fileFactoryReleaseResetsFileOffsetBitSetForFreeList() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64, 5);

    FileOffsetBitSet first = (FileOffsetBitSet) factory.open(1L, 0L);
    long firstPosition = first.getPosition();

    first.set(1L);
    factory.release(first);

    Assertions.assertEquals(0L, first.getStart());
    Assertions.assertEquals(64L, first.getEnd());
    Assertions.assertEquals(-1L, first.getBitSet().getUniqueId());
    Assertions.assertTrue(first.isEmpty());

    FileOffsetBitSet second = (FileOffsetBitSet) factory.open(2L, 128L);

    Assertions.assertSame(first, second);
    Assertions.assertEquals(firstPosition, second.getPosition());

    Assertions.assertEquals(128L, second.getStart());
    Assertions.assertEquals(192L, second.getEnd());
    Assertions.assertEquals(2L, second.getBitSet().getUniqueId());
    Assertions.assertTrue(second.isEmpty());

    factory.close();
  }

  @Test
  void fileOffsetBitSetCompareToUsesStart() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    try {
      FileOffsetBitSet first = (FileOffsetBitSet) factory.open(1L, 0L);
      FileOffsetBitSet sameStart = (FileOffsetBitSet) factory.open(2L, 0L);
      FileOffsetBitSet later = (FileOffsetBitSet) factory.open(3L, 64L);

      Assertions.assertEquals(0, first.compareTo(sameStart));
      Assertions.assertTrue(first.compareTo(later) < 0);
      Assertions.assertTrue(later.compareTo(first) > 0);
    } finally {
      factory.close();
    }
  }

  @Test
  void fileFactoryCloseReleasesMappedFile() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    factory.close();

    Assertions.assertTrue(Files.exists(file));
    Assertions.assertThrows(IllegalStateException.class, () -> bitset.set(1L));

    Files.delete(file);
    Assertions.assertFalse(Files.exists(file));
  }
}