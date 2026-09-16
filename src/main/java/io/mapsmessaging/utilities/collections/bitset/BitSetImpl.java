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
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.ListIterator;

@SuppressWarnings("squid:S7027")
@ToString
public class BitSetImpl implements BitSet {

  private final int capacity;
  private long uniqueId;

  private java.util.BitSet bitSet;

  public BitSetImpl(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("BitSet size must be greater than 0");
    }
    bitSet = new java.util.BitSet(size);
    capacity = size;
    uniqueId = 0;
  }

  public long[] getWords() {
    return bitSet.toLongArray();
  }

  @Override
  public boolean set(int bit) {
    int index = checkBoundary(bit);
    boolean previous = bitSet.get(index);
    bitSet.set(index);
    return !previous;
  }

  @Override
  public boolean clear(int bit) {
    int index = checkBoundary(bit);
    boolean previous = bitSet.get(index);
    bitSet.clear(index);
    return previous;
  }

  @Override
  public boolean isSet(int bit) {
    return bitSet.get(checkBoundary(bit));
  }

  @Override
  public boolean isSetAndClear(int bit) {
    boolean response = isSet(bit);
    if (response) {
      clear(bit);
    }
    return response;
  }

  @Override
  public void flip(int bit) {
    bitSet.flip(checkBoundary(bit));
  }

  @Override
  public void flip(int fromIndex, int toIndex) {
    if (fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex must not be greater than toIndex");
    }
    if (fromIndex == toIndex) {
      checkRangeEnd(fromIndex);
      return;
    }
    bitSet.flip(checkBoundary(fromIndex), checkRangeEnd(toIndex));
  }

  @Override
  public int length() {
    return capacity;
  }

  @Override
  public boolean isEmpty() {
    return bitSet.isEmpty();
  }

  @Override
  public int cardinality() {
    return bitSet.cardinality();
  }

  @Override
  public int nextSetBit(int fromIndex) {
    if (fromIndex < 0 || fromIndex >= capacity) {
      return -1;
    }
    int result = bitSet.nextSetBit(fromIndex);
    return result >= capacity ? -1 : result;
  }

  @Override
  public int nextSetBitAndClear(int fromIndex) {
    int response = nextSetBit(fromIndex);
    if (response >= 0) {
      clear(response);
    }
    return response;
  }

  @Override
  public int nextClearBit(int fromIndex) {
    if (fromIndex < 0 || fromIndex >= capacity) {
      return -1;
    }
    int result = bitSet.nextClearBit(fromIndex);
    return result >= capacity ? -1 : result;
  }

  @Override
  public int previousSetBit(int fromIndex) {
    if (fromIndex < 0) {
      return -1;
    }
    return bitSet.previousSetBit(Math.min(fromIndex, capacity - 1));
  }

  @Override
  public int previousClearBit(int fromIndex) {
    if (fromIndex < 0) {
      return -1;
    }
    int result = bitSet.previousClearBit(Math.min(fromIndex, capacity - 1));
    return result >= capacity ? -1 : result;
  }

  @Override
  public void clear() {
    bitSet.clear();
  }

  @Override
  public void and(@NonNull @NotNull BitSet map) {
    bitSet.and(asJavaBitSet(map));
    trimToCapacity();
  }

  @Override
  public void xor(@NonNull @NotNull BitSet map) {
    bitSet.xor(asJavaBitSet(map));
    trimToCapacity();
  }

  @Override
  public void or(@NonNull @NotNull BitSet map) {
    bitSet.or(asJavaBitSet(map));
    trimToCapacity();
  }

  @Override
  public void andNot(@NonNull @NotNull BitSet map) {
    bitSet.andNot(asJavaBitSet(map));
    trimToCapacity();
  }

  private java.util.BitSet asJavaBitSet(BitSet map) {
    if (map instanceof BitSetImpl bitSetImpl) {
      return bitSetImpl.bitSet;
    }
    if (map instanceof ByteBufferBackedBitMap byteBufferBackedBitMap) {
      long[] values = new long[byteBufferBackedBitMap.getLongCount()];
      for (int index = 0; index < values.length; index++) {
        values[index] = byteBufferBackedBitMap.getLong(index);
      }
      return java.util.BitSet.valueOf(values);
    }
    throw new UnsupportedOperationException("Unable to perform bitwise operation from " + map.getClass());
  }

  private void trimToCapacity() {
    int actualLength = bitSet.length();
    if (actualLength > capacity) {
      bitSet.clear(capacity, actualLength);
    }
  }

  @Override
  public Iterator<Integer> iterator() {
    return new BitSetIterator(this);
  }

  private int checkBoundary(int bit) {
    if (bit < 0 || bit >= capacity) {
      throw new IndexOutOfBoundsException(
          "Expecting range from 0 to " + (capacity - 1) + " received " + bit);
    }
    return bit;
  }

  private int checkRangeEnd(int bit) {
    if (bit < 0 || bit > capacity) {
      throw new IndexOutOfBoundsException(
          "Expecting range end from 0 to " + capacity + " received " + bit);
    }
    return bit;
  }

  @Override
  public ListIterator<Integer> listIterator() {
    return new BitSetListIterator(this);
  }

  @Override
  public long getUniqueId() {
    return uniqueId;
  }

  @Override
  public void setUniqueId(long uniqueId) {
    this.uniqueId = uniqueId;
  }
}
