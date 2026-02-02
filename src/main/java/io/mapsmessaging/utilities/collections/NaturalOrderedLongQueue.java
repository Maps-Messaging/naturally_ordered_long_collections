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

package io.mapsmessaging.utilities.collections;

import io.mapsmessaging.utilities.collections.bitset.BitSetFactory;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.Queue;

public class NaturalOrderedLongQueue extends NaturalOrderedCollection implements Queue<Long> {

  public NaturalOrderedLongQueue() {
  }

  public NaturalOrderedLongQueue(long id, @NonNull @NotNull BitSetFactory factory) {
    super(id, factory);
  }

  @Override
  public boolean offer(Long aLong) {
    return add(aLong);
  }

  @Override
  public Long remove() {
    Long response = poll();
    if (response == null || response == -1) {
      throw new NoSuchElementException();
    }
    return response;
  }

  @Override
  public Long poll() {
    if (tree.isEmpty()) {
      return null;
    }
    OffsetBitSet current = tree.firstEntry().getValue();

    long val = current.nextSetBitAndClear(current.getStart());
    if (val != -1) {
      //
      // Check and see if this was the last entry in the bit set, if so then remove the entry
      //
      if (current.isEmpty() && tree.size() > 1) {
        tree.remove(current.getStart());
        factory.release(current);
      }
      return val;
    }
    tree.remove(current.getStart());
    factory.release(current);
    return poll();
  }

  @Override
  public Long element() {
    return peek();
  }

  @Override
  public Long peek() {
    for (OffsetBitSet bitMap : tree.values()) {
      if (bitMap.cardinality() != 0) {
        return bitMap.nextSetBit(bitMap.getStart());
      }
    }
    return null;
  }
}
