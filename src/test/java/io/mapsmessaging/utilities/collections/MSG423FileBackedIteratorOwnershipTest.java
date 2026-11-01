/*
 *
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

import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MSG423FileBackedIteratorOwnershipTest {

  @Test
  void iteratorRemoveMustNotExposeReusedRecordFromAnotherOwner(@TempDir Path tempDir) throws IOException {
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(tempDir.resolve("owner-reuse.bin").toString(), 64)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(11L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(22L, factory);
      first.offer(1L);
      first.offer(64L);
      OffsetBitSet releasedRecord = first.tree.get(0L);
      Iterator<Long> iterator = first.iterator();

      Assertions.assertEquals(1L, iterator.next());
      iterator.remove();
      Assertions.assertEquals(1, factory.getFreeBitSets().size());
      Assertions.assertTrue(second.offer(130L));
      Assertions.assertSame(releasedRecord, second.tree.get(128L), "The second owner must reuse the released record");

      Assertions.assertEquals(64L, iterator.next());
      Assertions.assertFalse(iterator.hasNext());
      Assertions.assertEquals(List.of(64L), List.copyOf(first));
      Assertions.assertEquals(List.of(130L), List.copyOf(second));
    }
  }

  @Test
  void nextRechecksOwnershipAfterEarlierHasNext(@TempDir Path tempDir) throws IOException {
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(tempDir.resolve("prefetched-reuse.bin").toString(), 64)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(11L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(22L, factory);
      first.addAll(List.of(1L, 2L, 64L));
      Iterator<Long> iterator = first.iterator();

      Assertions.assertEquals(1L, iterator.next());
      Assertions.assertTrue(iterator.hasNext());
      Assertions.assertTrue(first.remove(1L));
      Assertions.assertTrue(first.remove(2L));
      Assertions.assertEquals(1, factory.getFreeBitSets().size());
      Assertions.assertTrue(second.offer(130L));

      Assertions.assertEquals(64L, iterator.next());
      Assertions.assertFalse(iterator.hasNext());
      Assertions.assertFalse(first.contains(130L));
      Assertions.assertTrue(second.contains(130L));
    }
  }

  @Test
  void unreachedReleasedWindowIsSkippedEvenAfterRecordReuse(@TempDir Path tempDir) throws IOException {
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(tempDir.resolve("future-window-reuse.bin").toString(), 64)) {
      NaturalOrderedLongQueue first = new NaturalOrderedLongQueue(11L, factory);
      NaturalOrderedLongQueue second = new NaturalOrderedLongQueue(22L, factory);
      first.addAll(List.of(-1L, 64L, 128L));
      Iterator<Long> iterator = first.iterator();
      OffsetBitSet releasedRecord = first.tree.get(64L);

      Assertions.assertTrue(first.remove(64L));
      Assertions.assertTrue(second.offer(194L));
      Assertions.assertSame(releasedRecord, second.tree.get(192L));

      Assertions.assertEquals(-1L, iterator.next());
      Assertions.assertEquals(128L, iterator.next());
      Assertions.assertFalse(iterator.hasNext());
      Assertions.assertEquals(List.of(-1L, 128L), List.copyOf(first));
    }
  }
}
