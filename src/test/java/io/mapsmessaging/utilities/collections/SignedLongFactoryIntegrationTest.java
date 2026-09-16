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

import io.mapsmessaging.utilities.collections.bitset.BitSetFactory;
import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ConcurrentSharedFileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.EphemeralFileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.HybridBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ManagedBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

class SignedLongFactoryIntegrationTest {

  private static final int WINDOW_SIZE = 192;
  private static final List<Long> BOUNDARIES = List.of(
      Long.MIN_VALUE,
      Long.MIN_VALUE + 1,
      -193L,
      -192L,
      -191L,
      -2L,
      -1L,
      0L,
      1L,
      2L,
      191L,
      192L,
      193L,
      Long.MAX_VALUE - 1,
      Long.MAX_VALUE
  );

  @Test
  void heapFactoryMatchesTreeSetForBoundaryAndRandomValues() {
    assertMatchesTreeSet(new BitSetFactoryImpl(WINDOW_SIZE), signedCorpus());
  }

  @Test
  void directBufferFactoryMatchesTreeSetForBoundaryAndRandomValues() {
    assertMatchesTreeSet(new ByteBufferBitSetFactoryImpl(WINDOW_SIZE), signedCorpus());
  }

  @Test
  void managedFactorySupportsNegativeUniqueIdAndValues() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(WINDOW_SIZE);
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
    for (long value : BOUNDARIES) {
      queue.offer(value);
    }
    Assertions.assertEquals(new TreeSet<>(BOUNDARIES), new TreeSet<>(queue));
    Assertions.assertEquals(List.of(-1L), factory.getUniqueIds());
  }

  @Test
  void ephemeralFileFactoryReloadsSignedValues() throws IOException {
    Path file = Files.createTempFile("signed-ephemeral", ".bit");
    Files.deleteIfExists(file);
    try {
      try (EphemeralFileBitSetFactoryImpl factory = new EphemeralFileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
        BOUNDARIES.forEach(queue::offer);
      }
      try (EphemeralFileBitSetFactoryImpl factory = new EphemeralFileBitSetFactoryImpl(file.toString(), WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
        Assertions.assertEquals(new ArrayList<>(new TreeSet<>(BOUNDARIES)), new ArrayList<>(queue));
      }
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void concurrentSharedFactoryReloadsNegativeId() throws IOException {
    Path directory = Files.createTempDirectory("signed-concurrent-shards");
    String base = directory.resolve("events").toString();
    try {
      try (ConcurrentSharedFileBitSetFactoryImpl factory =
               new ConcurrentSharedFileBitSetFactoryImpl(base, 3, WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(Long.MIN_VALUE, factory);
        BOUNDARIES.forEach(queue::offer);
      }
      try (ConcurrentSharedFileBitSetFactoryImpl factory =
               new ConcurrentSharedFileBitSetFactoryImpl(base, 3, WINDOW_SIZE)) {
        NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(Long.MIN_VALUE, factory);
        Assertions.assertEquals(new ArrayList<>(new TreeSet<>(BOUNDARIES)), new ArrayList<>(queue));
      }
    } finally {
      for (int shard = 0; shard < 3; shard++) {
        Files.deleteIfExists(Path.of(base + "_" + shard));
      }
      Files.deleteIfExists(directory);
    }
  }

  @Test
  void hybridFactorySupportsSignedWindowsThroughMigration() throws IOException {
    Path file = Files.createTempFile("signed-hybrid", ".bit");
    Files.deleteIfExists(file);
    ManagedBitSetFactoryImpl memory = new ManagedBitSetFactoryImpl(WINDOW_SIZE);
    FileBitSetFactoryImpl disk = new FileBitSetFactoryImpl(file.toString(), WINDOW_SIZE);

    try (HybridBitSetFactoryImpl factory = new HybridBitSetFactoryImpl(WINDOW_SIZE, 1, memory, disk)) {
      NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(-1L, factory);
      BOUNDARIES.forEach(queue::offer);
      Assertions.assertEquals(new ArrayList<>(new TreeSet<>(BOUNDARIES)), new ArrayList<>(queue));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private List<Long> signedCorpus() {
    TreeSet<Long> values = new TreeSet<>(BOUNDARIES);
    Random random = new Random(307L);
    for (int i = 0; i < 500; i++) {
      values.add(random.nextLong());
    }
    return new ArrayList<>(values);
  }

  private void assertMatchesTreeSet(BitSetFactory factory, List<Long> values) {
    NaturalOrderedCollection collection = new NaturalOrderedCollection(-1L, factory);
    TreeSet<Long> expected = new TreeSet<>();

    for (int i = values.size() - 1; i >= 0; i--) {
      long value = values.get(i);
      expected.add(value);
      collection.add(value);
    }

    Assertions.assertEquals(new ArrayList<>(expected), new ArrayList<>(collection));
    for (long value : expected) {
      Assertions.assertTrue(collection.contains(value), "Missing signed value " + value);
    }

    for (int i = 0; i < values.size(); i += 3) {
      long value = values.get(i);
      Assertions.assertEquals(expected.remove(value), collection.remove(value));
    }
    Assertions.assertEquals(new ArrayList<>(expected), new ArrayList<>(collection));
  }
}
