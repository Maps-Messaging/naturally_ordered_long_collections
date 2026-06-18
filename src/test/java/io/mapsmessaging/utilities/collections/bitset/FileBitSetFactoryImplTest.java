package io.mapsmessaging.utilities.collections.bitset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class FileBitSetFactoryImplTest {

  @TempDir
  Path tempDir;

  @Test
  void newFactoryCreatesFileOnFirstOpen() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");

    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    Assertions.assertFalse(Files.exists(file));

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    Assertions.assertTrue(Files.exists(file));
    Assertions.assertTrue(bitset.isSet(1L));
    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());

    factory.close();
  }

  @Test
  void releaseMovesBitsetToFreeListAndRemovesUniqueId() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    Assertions.assertEquals(1, factory.get(1L).size());
    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());

    factory.release(bitset);

    Assertions.assertTrue(factory.get(1L).isEmpty());
    Assertions.assertTrue(factory.getUniqueIds().isEmpty());
    Assertions.assertTrue(factory.get(-1L).size() >= 1);

    factory.close();
  }

  @Test
  void releasedBitsetIsClearedBeforeReuse() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet first = factory.open(1L, 0L);
    first.set(1L);
    first.set(63L);

    factory.release(first);

    OffsetBitSet second = factory.open(2L, 64L);

    Assertions.assertFalse(second.isSet(64L));
    Assertions.assertFalse(second.isSet(127L));
    Assertions.assertEquals(64L, second.getStart());
    Assertions.assertEquals(2L, second.getBitSet().getUniqueId());

    factory.close();
  }

  @Test
  void reloadExistingFileRestoresUsedBitsets() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");

    FileBitSetFactoryImpl firstFactory = new FileBitSetFactoryImpl(file.toString(), 64);
    OffsetBitSet first = firstFactory.open(1L, 0L);
    OffsetBitSet second = firstFactory.open(2L, 64L);

    first.set(1L);
    second.set(64L);

    firstFactory.close();

    FileBitSetFactoryImpl secondFactory = new FileBitSetFactoryImpl(file.toString(), 64);

    List<OffsetBitSet> uidOne = secondFactory.get(1L);
    List<OffsetBitSet> uidTwo = secondFactory.get(2L);

    Assertions.assertEquals(1, uidOne.size());
    Assertions.assertEquals(1, uidTwo.size());
    Assertions.assertTrue(uidOne.get(0).isSet(1L));
    Assertions.assertTrue(uidTwo.get(0).isSet(64L));

    secondFactory.close();
  }

  @Test
  void closeOnEmptyFactoryDeletesBackingFile() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    factory.release(bitset);

    Assertions.assertTrue(Files.exists(file));

    factory.close();

    Assertions.assertFalse(Files.exists(file));
  }

  @Test
  void openAfterCloseThrowsIllegalStateException() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    factory.open(1L, 0L);
    factory.close();

    Assertions.assertThrows(IllegalStateException.class, () -> factory.open(1L, 64L));
  }

  @Test
  void deleteRemovesBackingFileEvenWhenBitsetsAreUsed() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    Assertions.assertTrue(Files.exists(file));

    factory.delete();

    Assertions.assertFalse(Files.exists(file));
  }

  @Test
  void openAfterDeleteThrowsIllegalStateException() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    factory.open(1L, 0L);
    factory.delete();

    Assertions.assertThrows(IllegalStateException.class, () -> factory.open(1L, 64L));
  }

  @Test
  void cleanupIfPossibleDoesNotDeleteWhenUsedBitsetsExist() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    factory.cleanupIfPossible(0);

    Assertions.assertTrue(Files.exists(file));
    Assertions.assertEquals(1, factory.get(1L).size());

    factory.close();
  }

  @Test
  void cleanupIfPossibleDeletesEmptyFileWhenLargerThanMinimumSize() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    factory.release(bitset);

    Assertions.assertTrue(Files.exists(file));

    factory.cleanupIfPossible(0);

    Assertions.assertFalse(Files.exists(file));
  }

  @Test
  void cleanupIfPossibleKeepsEmptyFileWhenNotLargerThanMinimumSize() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    factory.release(bitset);

    long fileSize = Files.size(file);

    factory.cleanupIfPossible(fileSize);

    Assertions.assertTrue(Files.exists(file));

    factory.close();
  }

  @Test
  void getUniqueIdsReturnsDistinctUsedIdsInFileOrder() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    factory.open(1L, 0L);
    factory.open(2L, 64L);
    factory.open(1L, 128L);

    Assertions.assertEquals(List.of(1L, 2L), factory.getUniqueIds());

    factory.close();
  }

  @Test
  void getReturnsDefensiveCopy() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);

    List<OffsetBitSet> fetched = factory.get(1L);
    fetched.clear();

    Assertions.assertEquals(1, factory.get(1L).size());
    Assertions.assertSame(bitset, factory.get(1L).get(0));

    factory.close();
  }

  @Test
  void getUniqueIdsReturnsDefensiveCopy() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    factory.open(1L, 0L);

    List<Long> uniqueIds = factory.getUniqueIds();
    uniqueIds.clear();

    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());

    factory.close();
  }

  @Test
  void closeBitsetIsNoOpForFileFactory() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet bitset = factory.open(1L, 0L);
    bitset.set(1L);

    factory.close(bitset);

    Assertions.assertTrue(bitset.isSet(1L));
    Assertions.assertEquals(1, factory.get(1L).size());
    Assertions.assertEquals(List.of(1L), factory.getUniqueIds());

    factory.close();
  }

  @Test
  void releasedFreeRecordCanBeReusedForDifferentUniqueId() throws Exception {
    Path file = tempDir.resolve("bitsets.dat");
    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(file.toString(), 64);

    OffsetBitSet first = factory.open(1L, 0L);
    first.set(1L);

    factory.release(first);

    OffsetBitSet second = factory.open(2L, 128L);
    second.set(128L);

    Assertions.assertTrue(factory.get(1L).isEmpty());
    Assertions.assertEquals(1, factory.get(2L).size());
    Assertions.assertEquals(List.of(2L), factory.getUniqueIds());
    Assertions.assertTrue(second.isSet(128L));

    factory.close();
  }
}