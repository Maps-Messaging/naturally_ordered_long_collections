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

import lombok.Getter;
import lombok.NonNull;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;

public class DelegatingOffsetBitSet extends OffsetBitSet {

  @Getter
  private OffsetBitSet delegate;
  @Getter
  private BitSetFactory bitSetFactory;

  @Getter
  private final long uniqueId;


  public DelegatingOffsetBitSet(@NonNull OffsetBitSet initial, BitSetFactory bitSetFactory,  long uniqueId) {
    super(initial.getBitSet(), initial.getStart()); // Establish bounds
    this.bitSetFactory = bitSetFactory;
    this.delegate = initial;
    this.uniqueId = uniqueId;
  }

  public void swapDelegate(@NonNull OffsetBitSet replacement, BitSetFactory bitSetFactory) {
    this.delegate = replacement;
    this.bitSetFactory = bitSetFactory;
  }

  public void closeBitSet() {
    bitSetFactory.close(delegate);
  }

  public void releaseBitSet() {
    bitSetFactory.release(delegate);
  }

  @Override public boolean set(long bit) { return delegate.set(bit); }
  @Override public boolean clear(long bit) { return delegate.clear(bit); }
  @Override public boolean isSet(long bit) { return delegate.isSet(bit); }
  @Override public void flip(long bit) { delegate.flip(bit); }
  @Override public void flip(long fromIndex, long toIndex) { delegate.flip(fromIndex, toIndex); }
  @Override public int length() { return delegate.length(); }
  @Override public boolean isEmpty() { return delegate.isEmpty(); }
  @Override public int cardinality() { return delegate.cardinality(); }
  @Override public long nextSetBit(long fromIndex) { return delegate.nextSetBit(fromIndex); }
  @Override public long nextSetBitAndClear(long fromIndex) { return delegate.nextSetBitAndClear(fromIndex); }
  @Override public long nextClearBit(long fromIndex) { return delegate.nextClearBit(fromIndex); }
  @Override public long previousSetBit(long fromIndex) { return delegate.previousSetBit(fromIndex); }
  @Override public long previousClearBit(long fromIndex) { return delegate.previousClearBit(fromIndex); }

  @Override public void and(BitSet map) { delegate.and(map); }
  @Override public void or(BitSet map) { delegate.or(map); }
  @Override public void xor(BitSet map) { delegate.xor(map); }
  @Override public void andNot(BitSet map) { delegate.andNot(map); }

  @Override public void clear() { delegate.clear(); }
  @Override public void clearAll() { delegate.clearAll(); }

  @Override public Iterator<Long> iterator() { return delegate.iterator(); }
  @Override public ListIterator<Long> listIterator() { return delegate.listIterator(); }

  @Override public void reset(long start, long uniqueId) {
    delegate.reset(start, uniqueId);
    this.start = start;
    this.end = start + delegate.length();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DelegatingOffsetBitSet)) return false;
    return (this == o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uniqueId, getStart());
  }

}
