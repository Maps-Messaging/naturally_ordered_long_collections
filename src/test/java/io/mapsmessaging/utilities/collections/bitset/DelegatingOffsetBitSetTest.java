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

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class DelegatingOffsetBitSetTest {

  @Test
  void delegatesOperationsToWrappedOffsetBitSet() {
    RecordingBitSetFactory factory = new RecordingBitSetFactory(64);
    OffsetBitSet delegate = new OffsetBitSet(new BitSetImpl(64), 64L);
    DelegatingOffsetBitSet bitset = new DelegatingOffsetBitSet(delegate, factory, 1L);

    Assertions.assertEquals(64L, bitset.getStart());
    Assertions.assertEquals(128L, bitset.getEnd());
    Assertions.assertEquals(1L, bitset.getUniqueId());
    Assertions.assertSame(delegate, bitset.getDelegate());
    Assertions.assertSame(factory, bitset.getBitSetFactory());

    Assertions.assertTrue(bitset.set(64L));
    Assertions.assertTrue(delegate.isSet(64L));
    Assertions.assertTrue(bitset.isSet(64L));

    Assertions.assertTrue(bitset.clear(64L));
    Assertions.assertFalse(delegate.isSet(64L));
    Assertions.assertFalse(bitset.isSet(64L));
  }

  @Test
  void swapDelegateChangesBackingStorageAndFactory() {
    RecordingBitSetFactory firstFactory = new RecordingBitSetFactory(64);
    RecordingBitSetFactory secondFactory = new RecordingBitSetFactory(64);

    OffsetBitSet firstDelegate = new OffsetBitSet(new BitSetImpl(64), 0L);
    OffsetBitSet secondDelegate = new OffsetBitSet(new BitSetImpl(64), 0L);

    DelegatingOffsetBitSet bitset = new DelegatingOffsetBitSet(firstDelegate, firstFactory, 1L);

    bitset.set(1L);
    Assertions.assertTrue(firstDelegate.isSet(1L));
    Assertions.assertFalse(secondDelegate.isSet(1L));
    Assertions.assertSame(firstDelegate, bitset.getDelegate());
    Assertions.assertSame(firstFactory, bitset.getBitSetFactory());

    bitset.swapDelegate(secondDelegate, secondFactory);

    Assertions.assertSame(secondDelegate, bitset.getDelegate());
    Assertions.assertSame(secondFactory, bitset.getBitSetFactory());

    bitset.set(2L);

    Assertions.assertTrue(secondDelegate.isSet(2L));
    Assertions.assertFalse(firstDelegate.isSet(2L));
    Assertions.assertTrue(bitset.isSet(2L));
  }

  @Test
  void releaseBitSetDelegatesToCurrentFactory() {
    RecordingBitSetFactory firstFactory = new RecordingBitSetFactory(64);
    RecordingBitSetFactory secondFactory = new RecordingBitSetFactory(64);

    OffsetBitSet firstDelegate = new OffsetBitSet(new BitSetImpl(64), 0L);
    OffsetBitSet secondDelegate = new OffsetBitSet(new BitSetImpl(64), 64L);

    DelegatingOffsetBitSet bitset = new DelegatingOffsetBitSet(firstDelegate, firstFactory, 1L);
    bitset.swapDelegate(secondDelegate, secondFactory);

    bitset.releaseBitSet();

    Assertions.assertTrue(firstFactory.released.isEmpty());
    Assertions.assertTrue(firstFactory.closed.isEmpty());
    Assertions.assertEquals(List.of(secondDelegate), secondFactory.released);
    Assertions.assertTrue(secondFactory.closed.isEmpty());
  }

  @Test
  void closeBitSetDelegatesToCurrentFactory() {
    RecordingBitSetFactory firstFactory = new RecordingBitSetFactory(64);
    RecordingBitSetFactory secondFactory = new RecordingBitSetFactory(64);

    OffsetBitSet firstDelegate = new OffsetBitSet(new BitSetImpl(64), 0L);
    OffsetBitSet secondDelegate = new OffsetBitSet(new BitSetImpl(64), 64L);

    DelegatingOffsetBitSet bitset = new DelegatingOffsetBitSet(firstDelegate, firstFactory, 1L);
    bitset.swapDelegate(secondDelegate, secondFactory);

    bitset.closeBitSet();

    Assertions.assertTrue(firstFactory.released.isEmpty());
    Assertions.assertTrue(firstFactory.closed.isEmpty());
    Assertions.assertTrue(secondFactory.released.isEmpty());
    Assertions.assertEquals(List.of(secondDelegate), secondFactory.closed);
  }

  @Test
  void releaseAfterSwapDoesNotReleaseOldDelegate() {
    RecordingBitSetFactory firstFactory = new RecordingBitSetFactory(64);
    RecordingBitSetFactory secondFactory = new RecordingBitSetFactory(64);

    OffsetBitSet firstDelegate = new OffsetBitSet(new BitSetImpl(64), 0L);
    OffsetBitSet secondDelegate = new OffsetBitSet(new BitSetImpl(64), 64L);

    DelegatingOffsetBitSet bitset = new DelegatingOffsetBitSet(firstDelegate, firstFactory, 1L);
    bitset.swapDelegate(secondDelegate, secondFactory);
    bitset.releaseBitSet();

    Assertions.assertTrue(firstFactory.released.isEmpty());
    Assertions.assertEquals(List.of(secondDelegate), secondFactory.released);
  }

  @Test
  void closeAfterSwapDoesNotCloseOldDelegate() {
    RecordingBitSetFactory firstFactory = new RecordingBitSetFactory(64);
    RecordingBitSetFactory secondFactory = new RecordingBitSetFactory(64);

    OffsetBitSet firstDelegate = new OffsetBitSet(new BitSetImpl(64), 0L);
    OffsetBitSet secondDelegate = new OffsetBitSet(new BitSetImpl(64), 64L);

    DelegatingOffsetBitSet bitset = new DelegatingOffsetBitSet(firstDelegate, firstFactory, 1L);
    bitset.swapDelegate(secondDelegate, secondFactory);
    bitset.closeBitSet();

    Assertions.assertTrue(firstFactory.closed.isEmpty());
    Assertions.assertEquals(List.of(secondDelegate), secondFactory.closed);
  }

  @Test
  void resetUpdatesCurrentDelegateButKeepsWrapperUniqueId() {
    RecordingBitSetFactory factory = new RecordingBitSetFactory(64);
    OffsetBitSet delegate = new OffsetBitSet(new BitSetImpl(64), 0L);
    DelegatingOffsetBitSet bitset = new DelegatingOffsetBitSet(delegate, factory, 1L);

    bitset.set(1L);
    bitset.reset(128L, 99L);

    Assertions.assertEquals(128L, bitset.getStart());
    Assertions.assertEquals(192L, bitset.getEnd());
    Assertions.assertEquals(99L, bitset.getBitSet().getUniqueId());
    Assertions.assertEquals(1L, bitset.getUniqueId());
    Assertions.assertTrue(bitset.isEmpty());

    bitset.set(128L);
    Assertions.assertTrue(delegate.isSet(128L));
  }

  @Test
  void compareToFollowsCurrentDelegateStart() {
    RecordingBitSetFactory factory = new RecordingBitSetFactory(64);

    DelegatingOffsetBitSet first = new DelegatingOffsetBitSet(new OffsetBitSet(new BitSetImpl(64), 64L), factory, 1L);
    DelegatingOffsetBitSet sameStart = new DelegatingOffsetBitSet(new OffsetBitSet(new BitSetImpl(64), 64L), factory, 2L);
    DelegatingOffsetBitSet later = new DelegatingOffsetBitSet(new OffsetBitSet(new BitSetImpl(64), 128L), factory, 3L);

    Assertions.assertEquals(0, first.compareTo(sameStart));
    Assertions.assertTrue(first.compareTo(later) < 0);
    Assertions.assertTrue(later.compareTo(first) > 0);
  }
  private static class RecordingBitSetFactory extends BitSetFactory {

    private final List<OffsetBitSet> released = new ArrayList<>();
    private final List<OffsetBitSet> closed = new ArrayList<>();

    RecordingBitSetFactory(int size) {
      super(size);
    }

    @Override
    public OffsetBitSet open(long uniqueId, long id) {
      OffsetBitSet bitset = new OffsetBitSet(new BitSetImpl(windowSize), getStartIndex(id));
      bitset.getBitSet().setUniqueId(uniqueId);
      return bitset;
    }

    @Override
    public void release(@NonNull @NotNull OffsetBitSet bitset) {
      released.add(bitset);
    }

    @Override
    public void close(@NonNull @NotNull OffsetBitSet bitset) {
      closed.add(bitset);
    }

    @Override
    public List<OffsetBitSet> get(long uniqueId) {
      return new ArrayList<>();
    }

    @Override
    public List<Long> getUniqueIds() {
      return new ArrayList<>();
    }

    @Override
    public void close() throws IOException {
      // no-op
    }

    @Override
    public void delete() throws IOException {
      // no-op
    }
  }
}