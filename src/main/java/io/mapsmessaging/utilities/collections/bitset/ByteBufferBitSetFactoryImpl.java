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
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteBufferBitSetFactoryImpl extends BitSetFactory {

  public ByteBufferBitSetFactoryImpl(int size) {
    super(size);
  }

  @Override
  public OffsetBitSet open(long uniqueId, long id) {
    BitSet bs = new ByteBufferBackedBitMap(ByteBuffer.allocateDirect(windowSize / 8), 0);
    return new OffsetBitSet(bs, getStartIndex(id));
  }

  @Override
  public List<Long> getUniqueIds() {
    return new ArrayList<>();
  }

  @Override
  public void close(@NonNull @NotNull OffsetBitSet bitset) {
    // nothing to do
  }

  @Override
  public void release(OffsetBitSet bitset) {
    bitset.clearAll();
  }

  @Override
  public List<OffsetBitSet> get(long uniqueId) {
    return new ArrayList<>();
  }
}
