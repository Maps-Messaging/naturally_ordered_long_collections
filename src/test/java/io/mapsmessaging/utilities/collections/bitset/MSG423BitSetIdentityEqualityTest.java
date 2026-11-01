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
package io.mapsmessaging.utilities.collections.bitset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MSG423BitSetIdentityEqualityTest {

  @Test
  void releaseSecondDuplicateWindowMustKeepFirstInstanceTracked() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);
    OffsetBitSet first = factory.open(7L, 0L);
    OffsetBitSet second = factory.open(7L, 1L);
    Assertions.assertNotSame(first, second);
    Assertions.assertEquals(2, factory.get(7L).size());

    factory.release(second);

    Assertions.assertEquals(1, factory.get(7L).size());
    Assertions.assertSame(first, factory.get(7L).get(0));
    Assertions.assertEquals(List.of(7L), factory.getUniqueIds());
    factory.release(first);
    Assertions.assertTrue(factory.get(7L).isEmpty());
    Assertions.assertTrue(factory.getUniqueIds().isEmpty());
  }

  @Test
  void closeSecondDuplicateWindowMustKeepFirstInstanceTracked() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);
    OffsetBitSet first = factory.open(-1L, -1L);
    OffsetBitSet second = factory.open(-1L, -2L);

    factory.close(second);

    Assertions.assertEquals(1, factory.get(-1L).size());
    Assertions.assertSame(first, factory.get(-1L).get(0));
    factory.close(first);
    Assertions.assertTrue(factory.get(-1L).isEmpty());
  }

  @Test
  void releaseOfOtherOwnerWithMatchingWindowMustNotAffectFirstOwner() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);
    OffsetBitSet first = factory.open(7L, 0L);
    OffsetBitSet second = factory.open(8L, 0L);
    factory.release(second);

    Assertions.assertEquals(1, factory.get(7L).size());
    Assertions.assertSame(first, factory.get(7L).get(0));
    Assertions.assertTrue(factory.get(8L).isEmpty());
  }

  @Test
  void plainAndDelegatingEqualityMustBeSymmetric() {
    BitSetFactoryImpl factory = new BitSetFactoryImpl(64);
    OffsetBitSet plain = factory.open(7L, 0L);
    DelegatingOffsetBitSet delegated = new DelegatingOffsetBitSet(factory.open(7L, 0L), factory, 7L);

    Assertions.assertEquals(plain.equals(delegated), delegated.equals(plain));
    Assertions.assertNotEquals(plain, delegated);
    Assertions.assertNotEquals(delegated, plain);
    Assertions.assertEquals(delegated, delegated);
  }

  @Test
  void plainAndFileEqualityMustBeSymmetric(@TempDir Path tempDir) throws IOException {
    try (FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(tempDir.resolve("equality.bin").toString(), 64)) {
      OffsetBitSet plain = new OffsetBitSet(new BitSetImpl(64), 0L);
      OffsetBitSet mapped = factory.open(7L, 0L);

      Assertions.assertEquals(plain.equals(mapped), mapped.equals(plain));
      Assertions.assertNotEquals(plain, mapped);
      Assertions.assertNotEquals(mapped, plain);
    }
  }

  @Test
  void sameClassPlainWindowEqualityAndHashCodeRemainUnchanged() {
    OffsetBitSet first = new OffsetBitSet(new BitSetImpl(64), -64L);
    OffsetBitSet second = new OffsetBitSet(new BitSetImpl(64), -64L);
    OffsetBitSet next = new OffsetBitSet(new BitSetImpl(64), 0L);
    Assertions.assertEquals(first, second);
    Assertions.assertEquals(first.hashCode(), second.hashCode());
    Assertions.assertNotEquals(first, next);
    Assertions.assertEquals(0, first.compareTo(second));
  }
}
