/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2025 ] MapsMessaging B.V.
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

import io.mapsmessaging.utilities.collections.bitset.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EphermeralBitSetImplTest {

  @Test
  void testAddAndRetrieveInOrderAcrossThreshold() throws IOException {
    int size = 128;
    int threshold = 3;

    BitSetFactory memory = new ManagedBitSetFactoryImpl(size);
    BitSetFactory file = new EphemeralFileBitSetFactoryImpl("target/test-bitset.bin", size);
    HybridBitSetFactoryImpl hybrid = new HybridBitSetFactoryImpl(size, threshold, memory, file);
    try {

      NaturalOrderedCollection collection = new NaturalOrderedCollection(1, hybrid);

      // Push more than the threshold
      for (long i = 0; i < 500; i++) {
        collection.add(i);
      }

      List<Long> actual = new ArrayList<>();
      for (Long val : collection) {
        actual.add(val);
      }

      List<Long> expected = new ArrayList<>();
      for (long i = 0; i < 500; i++) {
        expected.add(i);
      }

      assertEquals(expected, actual, "Elements should be ordered and intact after migration");
    } finally {
      hybrid.close();
      Files.deleteIfExists(new File("target/test-bitset.bin").toPath());
    }
  }

  @Test
  void testMigrationHappensOnceOnly() throws IOException {
    int size = 64;
    int threshold = 2;

    BitSetFactory memory = spy(new ManagedBitSetFactoryImpl(size));
    BitSetFactory file = spy(new EphemeralFileBitSetFactoryImpl("target/test-bitset2.bin", size));
    HybridBitSetFactoryImpl hybrid = new HybridBitSetFactoryImpl(size, threshold, memory, file);

    try {
      for (long i = 0; i < 128 * threshold; i++) {
        hybrid.open(42L, i); // triggers migration once
      }

      verify(file, atLeastOnce()).open(eq(42L), anyLong());
      verify(memory, atMost(threshold)).open(eq(42L), anyLong());
    } finally {
      hybrid.close();
      Files.deleteIfExists(new File("target/test-bitset2.bin").toPath());
    }
  }

  @Test
  void testReleaseAndCloseDelegateToCorrectFactory() throws IOException {
    int size = 64;
    int threshold = 2;

    var memoryFactory = spy(new ManagedBitSetFactoryImpl(size));
    var fileFactory = spy(new EphemeralFileBitSetFactoryImpl("target/test-hybrid.bin", size));
    var hybridFactory = new HybridBitSetFactoryImpl(size, threshold, memoryFactory, fileFactory);
    try {
      // Step 1: Open bitsets below threshold
      OffsetBitSet bitset1 = hybridFactory.open(100L, 0L);
      OffsetBitSet bitset2 = hybridFactory.open(100L, 64L);

      // Both should be memory-backed
      hybridFactory.release(bitset1);
      hybridFactory.close(bitset2);
      verify(memoryFactory, atLeastOnce()).release(any());
      verify(memoryFactory).close(any());

      List<OffsetBitSet> bitsets = new ArrayList<>();
      for(int x=0; x<threshold*4; x++) {
        bitsets.add(hybridFactory.open(42L, 64*x));
      }

      // ensure migration has occured here

      for(OffsetBitSet bitset: bitsets) {
        hybridFactory.release(bitset);
      }
      verify(fileFactory,  atLeastOnce()).release(any());
    } finally {
      hybridFactory.close();
      Files.deleteIfExists(new File("target/test-hybrid.bin").toPath());
    }
  }

  @Test
  void testMigrationIsIsolatedPerUniqueId() throws IOException {
    int size = 64;
    int threshold = 2;

    var memoryFactory = spy(new ManagedBitSetFactoryImpl(size));
    var fileFactory = spy(new EphemeralFileBitSetFactoryImpl("target/test-hybrid-multi.bin", size));
    var hybridFactory = new HybridBitSetFactoryImpl(size, threshold, memoryFactory, fileFactory);

    try {
      // uniqueId 1 - below threshold
      OffsetBitSet u1b1 = hybridFactory.open(1L, 0L);
      OffsetBitSet u1b2 = hybridFactory.open(1L, 64L);

      // uniqueId 2 - exceeds threshold
      List<OffsetBitSet> u2bitsets = new ArrayList<>();
      for (int i = 0; i < threshold * 3; i++) {
        u2bitsets.add(hybridFactory.open(2L, i * 64L));
      }

      // Close and release all
      hybridFactory.release(u1b1);
      hybridFactory.close(u1b2);
      for (OffsetBitSet b : u2bitsets) {
        hybridFactory.release(b);
      }

      // Validation
      verify(fileFactory, atLeastOnce()).open(eq(2L), anyLong()); // Migration happened for uniqueId 2
      verify(fileFactory, never()).open(eq(1L), anyLong());       // No migration for uniqueId 1

    } finally {
      hybridFactory.close();
      Files.deleteIfExists(new File("target/test-hybrid-multi.bin").toPath());
    }
  }

}
