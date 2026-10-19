package io.mapsmessaging.utilities.collections.bitset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ByteBufferBitSetFactoryImplTest {

  @Test
  void openCreatesBitsetWithWindowStart() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 65L);

    Assertions.assertEquals(64L, bitset.getStart());
  }

  @Test
  void openAssignsUniqueIdToBackingBitset() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(123L, 0L);

    Assertions.assertEquals(123L, bitset.getBitSet().getUniqueId());
  }

  @Test
  void openCreatesIndependentBitsetsForSameWindow() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet first = factory.open(1L, 0L);
    OffsetBitSet second = factory.open(1L, 0L);

    first.set(1L);

    Assertions.assertTrue(first.isSet(1L));
    Assertions.assertFalse(second.isSet(1L));
  }

  @Test
  void releaseClearsBitset() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);
    bitset.set(63L);

    factory.release(bitset);

    Assertions.assertTrue(bitset.isEmpty());
  }

  @Test
  void closeBitsetDoesNotClearOrInvalidateBitset() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    factory.close(bitset);

    Assertions.assertTrue(bitset.isSet(1L));
  }

  @Test
  void closeFactoryDoesNotInvalidateExistingBitsets() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    factory.close();

    Assertions.assertTrue(bitset.isSet(1L));
  }

  @Test
  void deleteDoesNotInvalidateExistingBitsets() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    factory.delete();

    Assertions.assertTrue(bitset.isSet(1L));
  }

  @Test
  void getUniqueIdsAlwaysReturnsEmptyList() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    factory.open(1L, 0L);
    factory.open(2L, 64L);

    Assertions.assertTrue(factory.getUniqueIds().isEmpty());
  }

  @Test
  void getAlwaysReturnsEmptyList() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    factory.open(1L, 0L);

    Assertions.assertTrue(factory.get(1L).isEmpty());
  }

  @Test
  void releaseDoesNotAffectOtherBitsets() {
    ByteBufferBitSetFactoryImpl factory = new ByteBufferBitSetFactoryImpl(64);

    OffsetBitSet first = factory.open(1L, 0L);
    OffsetBitSet second = factory.open(1L, 0L);

    first.set(1L);
    second.set(2L);

    factory.release(first);

    Assertions.assertTrue(first.isEmpty());
    Assertions.assertTrue(second.isSet(2L));
  }
}