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

import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.BitSetImpl;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import io.mapsmessaging.utilities.collections.bitset.SharedFileBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

class SignedLongBadPathTest {

  private static final long FILE_MAGIC = 0x4E4F4C4342495432L;
  private static final int FILE_VERSION = 2;

  @TempDir
  Path tempDir;

  @Test
  void factoriesRejectNonPositiveWindowSizes() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BitSetFactoryImpl(0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BitSetFactoryImpl(-1));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new ByteBufferBitSetFactoryImpl(0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new ByteBufferBitSetFactoryImpl(-193));
  }

  @Test
  void sharedFactoryRejectsNonPositiveShardCounts() {
    String base = tempDir.resolve("bad-shards").toString();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SharedFileBitSetFactoryImpl(base, 0, 193));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SharedFileBitSetFactoryImpl(base, -1, 193));
  }

  @Test
  void extremaWindowsRejectValuesOutsideTheirRepresentableRange() {
    BitSetFactoryImpl factory = new BitSetFactoryImpl(193);

    long minStart = factory.getStartIndex(Long.MIN_VALUE);
    int minLength = factory.getWindowLength(minStart);
    OffsetBitSet minWindow = new OffsetBitSet(new BitSetImpl(193), minStart, minLength);
    long firstOutsideMinWindow = minStart + minLength;
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> minWindow.set(firstOutsideMinWindow));

    long maxStart = factory.getStartIndex(Long.MAX_VALUE);
    int maxLength = factory.getWindowLength(maxStart);
    OffsetBitSet maxWindow = new OffsetBitSet(new BitSetImpl(193), maxStart, maxLength);
    Assertions.assertThrows(IndexOutOfBoundsException.class, () -> maxWindow.set(maxStart - 1));
  }

  @Test
  void versionedFileRejectsUnsupportedVersion() throws Exception {
    Path file = tempDir.resolve("unsupported-version.bit");
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
      raf.writeLong(FILE_MAGIC);
      raf.writeInt(FILE_VERSION + 1);
      raf.writeInt(193);
    }

    Assertions.assertThrows(IOException.class,
        () -> new FileBitSetFactoryImpl(file.toString(), 193));
  }

  @Test
  void versionedFileRejectsMismatchedWindowSize() throws Exception {
    Path file = tempDir.resolve("wrong-window.bit");
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
      raf.writeLong(FILE_MAGIC);
      raf.writeInt(FILE_VERSION);
      raf.writeInt(193);
    }

    Assertions.assertThrows(IOException.class,
        () -> new FileBitSetFactoryImpl(file.toString(), 257));
  }
}
