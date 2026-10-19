package io.mapsmessaging.utilities.collections.bitset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ManagedBitSetFactoryImplTest {

  @Test
  void openRegistersUniqueId() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);

    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());
    Assertions.assertEquals(1, factory.get(1L).size());
    Assertions.assertSame(bitset, factory.get(1L).get(0));
  }

  @Test
  void releaseClearsBitset() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);
    bitset.set(63L);

    factory.release(bitset);

    Assertions.assertTrue(bitset.isEmpty());
  }

  @Test
  void releaseRemovesUniqueIdWhenLastBitsetIsReleased() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);

    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());

    factory.release(bitset);

    Assertions.assertTrue(factory.get(1L).isEmpty());
    Assertions.assertTrue(factory.getUniqueIds().isEmpty());
  }

  @Test
  void releaseKeepsUniqueIdWhenOtherBitsetsRemain() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);

    OffsetBitSet first = factory.open(1L, 0L);
    OffsetBitSet second = factory.open(1L, 64L);

    factory.release(first);

    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());
    Assertions.assertEquals(1, factory.get(1L).size());
    Assertions.assertSame(second, factory.get(1L).get(0));

    factory.release(second);

    Assertions.assertTrue(factory.get(1L).isEmpty());
    Assertions.assertTrue(factory.getUniqueIds().isEmpty());
  }

  @Test
  void closeBitsetReleasesIt() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    factory.close(bitset);

    Assertions.assertTrue(bitset.isEmpty());
    Assertions.assertTrue(factory.get(1L).isEmpty());
    Assertions.assertTrue(factory.getUniqueIds().isEmpty());
  }

  @Test
  void getReturnsDefensiveCopy() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);

    List<OffsetBitSet> fetched = factory.get(1L);
    fetched.clear();

    Assertions.assertEquals(1, factory.get(1L).size());
    Assertions.assertSame(bitset, factory.get(1L).get(0));
  }

  @Test
  void getUniqueIdsReturnsDefensiveCopy() {
    ManagedBitSetFactoryImpl factory = new ManagedBitSetFactoryImpl(64);

    factory.open(1L, 0L);

    List<Long> uniqueIds = factory.getUniqueIds();
    uniqueIds.clear();

    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());
  }
}