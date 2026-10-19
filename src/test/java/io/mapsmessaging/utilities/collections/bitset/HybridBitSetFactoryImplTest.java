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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

class HybridBitSetFactoryImplTest {
  @Test
  void thresholdMigrationIsPerUniqueId() throws Exception {
    ManagedBitSetFactoryImpl memoryFactory = new ManagedBitSetFactoryImpl(64);
    ManagedBitSetFactoryImpl fileFactory = new ManagedBitSetFactoryImpl(64);
    HybridBitSetFactoryImpl factory = new HybridBitSetFactoryImpl(64, 1, memoryFactory, fileFactory);

    OffsetBitSet firstUidFirst = factory.open(1L, 0L);
    firstUidFirst.set(1L);

    OffsetBitSet secondUidFirst = factory.open(2L, 0L);
    secondUidFirst.set(2L);

    OffsetBitSet firstUidSecond = factory.open(1L, 64L);
    firstUidSecond.set(64L);

    Assertions.assertTrue(memoryFactory.get(1L).isEmpty());
    Assertions.assertEquals(2, fileFactory.get(1L).size());

    Assertions.assertEquals(1, memoryFactory.get(2L).size());
    Assertions.assertTrue(fileFactory.get(2L).isEmpty());

    Assertions.assertTrue(firstUidFirst.isSet(1L));
    Assertions.assertTrue(firstUidSecond.isSet(64L));
    Assertions.assertTrue(secondUidFirst.isSet(2L));
  }
  @Test
  void openAfterThresholdRegistersReturnedDelegate() throws Exception {
    ManagedBitSetFactoryImpl memoryFactory = new ManagedBitSetFactoryImpl(64);
    ManagedBitSetFactoryImpl fileFactory = new ManagedBitSetFactoryImpl(64);
    HybridBitSetFactoryImpl factory = new HybridBitSetFactoryImpl(64, 1, memoryFactory, fileFactory);

    OffsetBitSet first = factory.open(1L, 0L);
    OffsetBitSet second = factory.open(1L, 64L);

    Assertions.assertInstanceOf(DelegatingOffsetBitSet.class, first);
    Assertions.assertInstanceOf(DelegatingOffsetBitSet.class, second);

    Assertions.assertEquals(2, activeDelegateCount(factory, 1L));
  }

  @Test
  void openAfterThresholdStoresNewWindowsInFileFactory() throws Exception {
    ManagedBitSetFactoryImpl memoryFactory = new ManagedBitSetFactoryImpl(64);
    ManagedBitSetFactoryImpl fileFactory = new ManagedBitSetFactoryImpl(64);
    HybridBitSetFactoryImpl factory = new HybridBitSetFactoryImpl(64, 1, memoryFactory, fileFactory);

    OffsetBitSet first = factory.open(1L, 0L);
    first.set(1L);

    OffsetBitSet second = factory.open(1L, 64L);
    second.set(64L);

    Assertions.assertTrue(memoryFactory.get(1L).isEmpty());
    Assertions.assertEquals(2, fileFactory.get(1L).size());
    Assertions.assertTrue(first.isSet(1L));
    Assertions.assertTrue(second.isSet(64L));
  }

  @Test
  void releaseRemovesTrackedDelegates() throws Exception {
    ManagedBitSetFactoryImpl memoryFactory = new ManagedBitSetFactoryImpl(64);
    ManagedBitSetFactoryImpl fileFactory = new ManagedBitSetFactoryImpl(64);
    HybridBitSetFactoryImpl factory = new HybridBitSetFactoryImpl(64, 1, memoryFactory, fileFactory);

    OffsetBitSet first = factory.open(1L, 0L);
    OffsetBitSet second = factory.open(1L, 64L);

    Assertions.assertEquals(2, activeDelegateCount(factory, 1L));

    factory.release(second);
    Assertions.assertEquals(1, activeDelegateCount(factory, 1L));

    factory.release(first);
    Assertions.assertEquals(0, activeDelegateCount(factory, 1L));
  }

  @Test
  void closeClearsTrackedDelegates() throws Exception {
    ManagedBitSetFactoryImpl memoryFactory = new ManagedBitSetFactoryImpl(64);
    ManagedBitSetFactoryImpl fileFactory = new ManagedBitSetFactoryImpl(64);
    HybridBitSetFactoryImpl factory = new HybridBitSetFactoryImpl(64, 1, memoryFactory, fileFactory);

    factory.open(1L, 0L);
    factory.open(1L, 64L);

    Assertions.assertEquals(2, activeDelegateCount(factory, 1L));

    factory.close();

    Assertions.assertEquals(0, activeDelegateCount(factory, 1L));
  }

  @Test
  void migrationPreservesExistingBits() throws Exception {
    ManagedBitSetFactoryImpl memoryFactory = new ManagedBitSetFactoryImpl(64);
    ManagedBitSetFactoryImpl fileFactory = new ManagedBitSetFactoryImpl(64);
    HybridBitSetFactoryImpl factory = new HybridBitSetFactoryImpl(64, 1, memoryFactory, fileFactory);

    OffsetBitSet first = factory.open(1L, 0L);
    first.set(1L);
    first.set(63L);

    OffsetBitSet second = factory.open(1L, 64L);
    second.set(64L);

    Assertions.assertTrue(first.isSet(1L));
    Assertions.assertTrue(first.isSet(63L));
    Assertions.assertTrue(second.isSet(64L));
    Assertions.assertTrue(memoryFactory.get(1L).isEmpty());
    Assertions.assertEquals(2, fileFactory.get(1L).size());
  }

  @SuppressWarnings("unchecked")
  private int activeDelegateCount(HybridBitSetFactoryImpl factory, long uniqueId) throws Exception {
    Field field = HybridBitSetFactoryImpl.class.getDeclaredField("activeDelegates");
    field.setAccessible(true);

    Map<Long, List<DelegatingOffsetBitSet>> activeDelegates =
        (Map<Long, List<DelegatingOffsetBitSet>>) field.get(factory);

    return activeDelegates.getOrDefault(uniqueId, List.of()).size();
  }
}