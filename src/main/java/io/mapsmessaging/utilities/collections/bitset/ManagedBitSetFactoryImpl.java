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

import java.util.*;

public class ManagedBitSetFactoryImpl extends BitSetFactory {

  private final Map<Long, List<OffsetBitSet>> map = new HashMap<>();

  public ManagedBitSetFactoryImpl(int size) {
    super(size);
  }

  @Override
  public synchronized OffsetBitSet open(long uniqueId, long id) {
    long start = getStartIndex(id);
    OffsetBitSet bitset = new OffsetBitSet(new BitSetImpl(windowSize), start);
    bitset.getBitSet().setUniqueId(uniqueId);
    map.computeIfAbsent(uniqueId, k -> new ArrayList<>()).add(bitset);
    return bitset;
  }

  @Override
  public synchronized void release(@NonNull OffsetBitSet bitset) {
    bitset.clearAll();
    long uid = bitset.getBitSet().getUniqueId();
    map.getOrDefault(uid, List.of()).remove(bitset);
  }

  @Override
  public synchronized void close(@NonNull OffsetBitSet bitset) {
    release(bitset);
  }

  @Override
  public synchronized List<OffsetBitSet> get(long uniqueId) {
    return new ArrayList<>(map.getOrDefault(uniqueId, Collections.emptyList()));
  }

  @Override
  public synchronized List<Long> getUniqueIds() {
    return new ArrayList<>(map.keySet());
  }
}
