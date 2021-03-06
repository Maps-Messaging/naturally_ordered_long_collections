/*
 *
 *   Copyright [ 2020 - 2021 ] [Matthew Buckton]
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package io.mapsmessaging.utilities.collections.bitset;

import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.And;
import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.AndNot;
import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.Or;
import io.mapsmessaging.utilities.collections.bitset.BitWiseOperator.Xor;
import java.util.Iterator;
import java.util.ListIterator;
import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

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
    boolean previous = bitSet.get(checkBoundary(bit));
    bitSet.set(checkBoundary(bit));
    return !previous;
  }

  @Override
  public boolean clear(int bit) {
    boolean previous = bitSet.get(checkBoundary(bit));
    bitSet.clear(checkBoundary(bit));
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
    bitSet.flip(checkBoundary(fromIndex), checkBoundary(toIndex));
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
    if (map instanceof BitSetImpl) {
      bitSet.and(((BitSetImpl) map).bitSet);
    } else if (map instanceof ByteBufferBackedBitMap) {
      bitwiseCompute((ByteBufferBackedBitMap) map, new And());
    }
  }

  @Override
  public void xor(@NonNull @NotNull BitSet map) {
    if (map instanceof BitSetImpl) {
      bitSet.xor(((BitSetImpl) map).bitSet);
    } else if (map instanceof ByteBufferBackedBitMap) {
      bitwiseCompute((ByteBufferBackedBitMap) map, new Xor());
    }
  }

  @Override
  public void or(@NonNull @NotNull BitSet map) {
    if (map instanceof BitSetImpl) {
      bitSet.or(((BitSetImpl) map).bitSet);
    } else if (map instanceof ByteBufferBackedBitMap) {
      bitwiseCompute((ByteBufferBackedBitMap) map, new Or());
    }
  }

  @Override
  public void andNot(@NonNull @NotNull BitSet map) {
    if (map instanceof BitSetImpl) {
      bitSet.andNot(((BitSetImpl) map).bitSet);
    } else if (map instanceof ByteBufferBackedBitMap) {
      bitwiseCompute((ByteBufferBackedBitMap) map, new AndNot());
    }
  }

  private void bitwiseCompute(@NonNull @NotNull ByteBufferBackedBitMap map, @NonNull @NotNull BitWiseOperator operator) {
    long[] longs = bitSet.toLongArray();
    int longCount = map.getLongCount();
    if(longs.length < longCount){
      var expand = new long[longCount];
      System.arraycopy(longs, 0, expand, 0, longs.length);
      longs = expand;
    }

    int len = Math.min(map.getLongCount(), longs.length);
    for (var x = 0; x < len; x++) {
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

  private int checkBoundary(int bit) {
    //
    // Must be within range
    //
    if (bit < 0 || bit > capacity) {
      throw new IndexOutOfBoundsException(
          "Expecting range from 0 to " + (capacity) + " received " + bit);
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
