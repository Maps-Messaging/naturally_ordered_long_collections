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

import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.And;
import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.AndNot;
import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.Or;
import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.Xor;
import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.ListIterator;

@ToString
public class BitSetImpl implements BitSet {

  private final int capacity;
  private long uniqueId;

  private java.util.BitSet bitSet;

  public BitSetImpl(int size) {
    bitSet = new java.util.BitSet(size);
    capacity = size;
    uniqueId = 0;
  }

  public long[] getWords() {
    return bitSet.toLongArray();
  }

  @Override
  public boolean set(int bit) {
    int index = checkBitBoundary(bit);
    boolean previous = bitSet.get(index);
    bitSet.set(index);
    return !previous;
  }

  @Override
  public boolean clear(int bit) {
    int index = checkBitBoundary(bit);
    boolean previous = bitSet.get(index);
    bitSet.clear(index);
    return previous;
  }

  @Override
  public boolean isSet(int bit) {
    return bitSet.get(checkBitBoundary(bit));
  }

  @Override
  public boolean isSetAndClear(int bit) {
    int index = checkBitBoundary(bit);
    boolean response = bitSet.get(index);
    if (response) {
      bitSet.clear(index);
    }
    return response;
  }

  @Override
  public void flip(int bit) {
    bitSet.flip(checkBitBoundary(bit));
  }

  @Override
  public void flip(int fromIndex, int toIndex) {
    int from = checkRangeBoundary(fromIndex);
    int to = checkRangeBoundary(toIndex);
    bitSet.flip(from, to);
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
    return bitSet.nextSetBit(fromIndex);
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
    return bitSet.nextClearBit(fromIndex);
  }

  @Override
  public int previousSetBit(int fromIndex) {
    return bitSet.previousSetBit(fromIndex);
  }

  @Override
  public int previousClearBit(int fromIndex) {
    return bitSet.previousClearBit(fromIndex);
  }

  @Override
  public void clear() {
    bitSet.clear();
  }

  @Override
  public void and(@NonNull @NotNull BitSet map) {
    if (map instanceof BitSetImpl bitSetImpl) {
      bitSet.and(bitSetImpl.bitSet);
    } else if (map instanceof ByteBufferBackedBitMap byteBufferBackedBitMap) {
      bitwiseCompute(byteBufferBackedBitMap, new And());
    }
  }

  @Override
  public void xor(@NonNull @NotNull BitSet map) {
    if (map instanceof BitSetImpl bitSetImpl) {
      bitSet.xor(bitSetImpl.bitSet);
    } else if (map instanceof ByteBufferBackedBitMap byteBufferBackedBitMap) {
      bitwiseCompute(byteBufferBackedBitMap, new Xor());
    }
  }

  @Override
  public void or(@NonNull @NotNull BitSet map) {
    if (map instanceof BitSetImpl bitSetImpl) {
      bitSet.or(bitSetImpl.bitSet);
    } else if (map instanceof ByteBufferBackedBitMap byteBufferBackedBitMap) {
      bitwiseCompute(byteBufferBackedBitMap, new Or());
    }
  }

  @Override
  public void andNot(@NonNull @NotNull BitSet map) {
    if (map instanceof BitSetImpl bitSetImpl) {
      bitSet.andNot(bitSetImpl.bitSet);
    } else if (map instanceof ByteBufferBackedBitMap byteBufferBackedBitMap) {
      bitwiseCompute(byteBufferBackedBitMap, new AndNot());
    }
  }

  private void bitwiseCompute(@NonNull @NotNull ByteBufferBackedBitMap map, @NonNull @NotNull BitWiseOperator operator) {
    long[] longs = bitSet.toLongArray();
    int longCount = map.getLongCount();
    if (longs.length < longCount) {
      var expanded = new long[longCount];
      System.arraycopy(longs, 0, expanded, 0, longs.length);
      longs = expanded;
    }

    int length = Math.min(map.getLongCount(), longs.length);
    for (var x = 0; x < length; x++) {
      var lhs = longs[x];
      var rhs = map.getLong(x);
      longs[x] = operator.operation(lhs, rhs);
    }
    bitSet = java.util.BitSet.valueOf(longs);
  }

  @Override
  public Iterator<Integer> iterator() {
    return new BitSetIterator(this);
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

  private int checkBitBoundary(int bit) {
    if (bit < 0 || bit >= capacity) {
      throw new IndexOutOfBoundsException(
          "Expecting range from 0 to " + (capacity - 1) + " received " + bit);
    }
    return bit;
  }

  private int checkRangeBoundary(int bit) {
    if (bit < 0 || bit > capacity) {
      throw new IndexOutOfBoundsException(
          "Expecting range from 0 to " + capacity + " received " + bit);
    }
    return bit;
  }
}