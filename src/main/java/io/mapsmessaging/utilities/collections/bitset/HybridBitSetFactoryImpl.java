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

package io.mapsmessaging.utilities.collections.bitset;

import lombok.NonNull;

import java.io.IOException;
import java.util.*;

public class HybridBitSetFactoryImpl extends BitSetFactory {

  private final BitSetFactory memoryFactory;
  private final BitSetFactory fileFactory;
  private final int threshold;
  private final Map<Long, List<DelegatingOffsetBitSet>> activeDelegates = new HashMap<>();


  public HybridBitSetFactoryImpl(int size, int threshold,
                                 @NonNull BitSetFactory memoryFactory,
                                 @NonNull BitSetFactory fileFactory) {
    super(size);
    this.memoryFactory = memoryFactory;
    this.fileFactory = fileFactory;
    this.threshold = threshold;
  }

  @Override
  public void close() throws IOException {
    memoryFactory.close();
    fileFactory.close();
  }

  @Override
  public synchronized void release(@NonNull OffsetBitSet bitset) {
    if(bitset instanceof DelegatingOffsetBitSet) {
      DelegatingOffsetBitSet delegating = (DelegatingOffsetBitSet) bitset;
      delegating.releaseBitSet();
      removeDelegate(delegating.getUniqueId(), delegating);
    }
  }

  @Override
  public synchronized void close(@NonNull OffsetBitSet bitset) {
    if(bitset instanceof DelegatingOffsetBitSet) {
      DelegatingOffsetBitSet delegating = (DelegatingOffsetBitSet) bitset;
      delegating.closeBitSet();
      removeDelegate(delegating.getUniqueId(), delegating);
    }
  }

  @Override
  public List<OffsetBitSet> get(long uniqueId) {
    List<OffsetBitSet> result = new ArrayList<>();
    result.addAll(memoryFactory.get(uniqueId));
    result.addAll(fileFactory.get(uniqueId));
    return result;
  }

  @Override
  public List<Long> getUniqueIds() {
    Set<Long> ids = new LinkedHashSet<>();
    ids.addAll(memoryFactory.getUniqueIds());
    ids.addAll(fileFactory.getUniqueIds());
    return new ArrayList<>(ids);
  }

  @Override
  public synchronized OffsetBitSet open(long uniqueId, long id) throws IOException {
    List<DelegatingOffsetBitSet> delegates = activeDelegates.computeIfAbsent(uniqueId, k -> new ArrayList<>());
    if (delegates.size()  == threshold) {
      migrateToDisk(uniqueId);
    }

    DelegatingOffsetBitSet wrapper;
    if (delegates.size()  >= threshold) {
      return new DelegatingOffsetBitSet(fileFactory.open(uniqueId, id), fileFactory, uniqueId);
    }
    else{
      wrapper = new DelegatingOffsetBitSet(memoryFactory.open(uniqueId, id), memoryFactory, uniqueId);
    }
    delegates.add(wrapper);
    return wrapper;
  }

  private void removeDelegate(long uniqueId, DelegatingOffsetBitSet bitset) {
    List<DelegatingOffsetBitSet> list = activeDelegates.get(uniqueId);
    if (list != null) {
      list.remove(bitset);
      if (list.isEmpty()) {
        activeDelegates.remove(uniqueId);
      }
    }
  }


  private void migrateToDisk(long uniqueId) throws IOException {
    List<DelegatingOffsetBitSet> delegates = activeDelegates.getOrDefault(uniqueId, List.of());
    if (delegates.isEmpty()) return;

    for (DelegatingOffsetBitSet delegate : delegates) {
      if (delegate.getBitSetFactory() != memoryFactory) {
        continue; // Already disk-backed
      }
      long start = delegate.getStart();
      OffsetBitSet diskSet = fileFactory.open(uniqueId, start);
      diskSet.or(delegate.getBitSet());
      memoryFactory.release(delegate.getDelegate());
      delegate.swapDelegate(diskSet, fileFactory);
    }
  }
}
