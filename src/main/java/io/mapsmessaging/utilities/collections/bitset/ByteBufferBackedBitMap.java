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
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.ListIterator;

@SuppressWarnings("squid:S7027")
public class ByteBufferBackedBitMap implements BitSet {

  private static final long LONG_MASK = -1L;
  private static final int LONG_BIT_SHIFT = 6;
  private static final int LONG_SIZE = Long.BYTES;
  private static final int LONG_BITS = Long.SIZE;

  private final int longs;
  private final int capacity;
  private final int offset;

  private ByteBuffer backing;
  @Setter
  @Getter
  private long uniqueId;

  public ByteBufferBackedBitMap(@NonNull @NotNull ByteBuffer buffer, int offset) {
    this(buffer, offset, 0);
  }

  public ByteBufferBackedBitMap(@NonNull @NotNull ByteBuffer buffer, int offset, long uniqueId) {
    if (offset < 0 || offset > buffer.capacity()) {
      throw new IndexOutOfBoundsException("Invalid byte buffer offset " + offset);
    }
    this.uniqueId = uniqueId;
    backing = buffer;
    this.offset = offset;
    int availableBytes = backing.capacity() - offset;
    longs = availableBytes / LONG_SIZE;
    if (longs <= 0) {
      throw new IllegalArgumentException("ByteBuffer must contain at least one complete long");
    }
    capacity = longs * LONG_BITS;
  }

  public @NonNull @NotNull ByteBuffer getBacking() {
    return backing;
  }

  protected @NonNull @NotNull ByteBuffer clearBacking() {
    ByteBuffer buffer = backing;
    backing = null;
    return buffer;
  }

  public long getOffset() {
    return offset;
  }

  @Override
  public boolean set(int bit) {
    int internalBit = checkBoundary(bit);
    int wordIndex = internalBit >>> LONG_BIT_SHIFT;
    long bitMask = 1L << internalBit;
    long value = getWord(wordIndex);
    long original = value;
    value |= bitMask;
    putWord(wordIndex, value);
    return value != original;
  }

  @Override
  public boolean clear(int bit) {
    int internalBit = checkBoundary(bit);
    int wordIndex = internalBit >>> LONG_BIT_SHIFT;
    long bitMask = 1L << internalBit;
    long value = getWord(wordIndex);
    long original = value;
    value &= ~bitMask;
    putWord(wordIndex, value);
    return value != original;
  }

  @Override
  public boolean isSet(int bit) {
    int internalBit = checkBoundary(bit);
    int wordIndex = internalBit >>> LONG_BIT_SHIFT;
    long bitMask = 1L << internalBit;
    return (getWord(wordIndex) & bitMask) != 0;
  }

  @Override
  public boolean isSetAndClear(int bit) {
    int internalBit = checkBoundary(bit);
    int wordIndex = internalBit >>> LONG_BIT_SHIFT;
    long bitMask = 1L << internalBit;
    long value = getWord(wordIndex);
    boolean set = (value & bitMask) != 0;
    if (set) {
      putWord(wordIndex, value & ~bitMask);
    }
    return set;
  }

  @Override
  public void flip(int bit) {
    int internalBit = checkBoundary(bit);
    int wordIndex = internalBit >>> LONG_BIT_SHIFT;
    putWord(wordIndex, getWord(wordIndex) ^ (1L << internalBit));
  }

  @Override
  public void flip(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);
    if (fromIndex == toIndex) {
      return;
    }

    int startWordIndex = fromIndex >>> LONG_BIT_SHIFT;
    int endWordIndex = (toIndex - 1) >>> LONG_BIT_SHIFT;
    long firstWordMask = LONG_MASK << fromIndex;
    long lastWordMask = LONG_MASK >>> -toIndex;

    if (startWordIndex == endWordIndex) {
      putWord(startWordIndex, getWord(startWordIndex) ^ (firstWordMask & lastWordMask));
      return;
    }

    putWord(startWordIndex, getWord(startWordIndex) ^ firstWordMask);
    for (int wordIndex = startWordIndex + 1; wordIndex < endWordIndex; wordIndex++) {
      putWord(wordIndex, getWord(wordIndex) ^ LONG_MASK);
    }
    putWord(endWordIndex, getWord(endWordIndex) ^ lastWordMask);
  }

  @Override
  public int length() {
    return capacity;
  }

  @Override
  public int cardinality() {
    int sum = 0;
    for (int wordIndex = 0; wordIndex < longs; wordIndex++) {
      sum += Long.bitCount(getWord(wordIndex));
    }
    return sum;
  }

  @Override
  public boolean isEmpty() {
    for (int wordIndex = 0; wordIndex < longs; wordIndex++) {
      if (getWord(wordIndex) != 0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int nextSetBit(int fromIndex) {
    if (fromIndex < 0 || fromIndex >= capacity) {
      return -1;
    }

    int wordIndex = fromIndex >>> LONG_BIT_SHIFT;
    long word = getWord(wordIndex) & (LONG_MASK << fromIndex);
    while (true) {
      if (word != 0) {
        return (wordIndex * LONG_BITS) + Long.numberOfTrailingZeros(word);
      }
      if (++wordIndex >= longs) {
        return -1;
      }
      word = getWord(wordIndex);
    }
  }

  @Override
  public int nextSetBitAndClear(int fromIndex) {
    int bit = nextSetBit(fromIndex);
    if (bit >= 0) {
      clear(bit);
    }
    return bit;
  }

  @Override
  public int nextClearBit(int fromIndex) {
    if (fromIndex < 0 || fromIndex >= capacity) {
      return -1;
    }

    int wordIndex = fromIndex >>> LONG_BIT_SHIFT;
    long word = ~getWord(wordIndex) & (LONG_MASK << fromIndex);
    while (true) {
      if (word != 0) {
        return (wordIndex * LONG_BITS) + Long.numberOfTrailingZeros(word);
      }
      if (++wordIndex >= longs) {
        return -1;
      }
      word = ~getWord(wordIndex);
    }
  }

  @Override
  public int previousSetBit(int fromIndex) {
    if (fromIndex < 0) {
      return -1;
    }
    int index = Math.min(fromIndex, capacity - 1);
    int wordIndex = index >>> LONG_BIT_SHIFT;
    long word = getWord(wordIndex) & (LONG_MASK >>> -(index + 1));
    while (true) {
      if (word != 0) {
        return ((wordIndex + 1) * LONG_BITS) - 1 - Long.numberOfLeadingZeros(word);
      }
      if (--wordIndex < 0) {
        return -1;
      }
      word = getWord(wordIndex);
    }
  }

  @Override
  public int previousClearBit(int fromIndex) {
    if (fromIndex < 0) {
      return -1;
    }
    int index = Math.min(fromIndex, capacity - 1);
    int wordIndex = index >>> LONG_BIT_SHIFT;
    long word = ~getWord(wordIndex) & (LONG_MASK >>> -(index + 1));
    while (true) {
      if (word != 0) {
        return ((wordIndex + 1) * LONG_BITS) - 1 - Long.numberOfLeadingZeros(word);
      }
      if (--wordIndex < 0) {
        return -1;
      }
      word = ~getWord(wordIndex);
    }
  }

  @Override
  public void clear() {
    for (int wordIndex = 0; wordIndex < longs; wordIndex++) {
      putWord(wordIndex, 0L);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ID:").append(uniqueId);
    sb.append(" Size:").append(cardinality()).append(" Offset:").append(offset).append(" {");
    int counter = 0;
    int index = 0;
    while (counter < 1024 && index < capacity) {
      index = nextSetBit(index);
      if (index < 0) {
        break;
      }
      sb.append(index).append(", ");
      index++;
      counter++;
    }
    sb.append("}");
    return sb.toString();
  }

  @Override
  public void and(BitSet map) {
    bitwiseCompute(map, new And());
  }

  @Override
  public void xor(BitSet map) {
    bitwiseCompute(map, new Xor());
  }

  @Override
  public void or(BitSet map) {
    bitwiseCompute(map, new Or());
  }

  @Override
  public void andNot(BitSet map) {
    bitwiseCompute(map, new AndNot());
  }

  private void bitwiseCompute(BitSet test, BitWiseOperator operator) {
    if (test instanceof ByteBufferBackedBitMap map) {
      for (int wordIndex = 0; wordIndex < longs; wordIndex++) {
        long rhs = wordIndex < map.longs ? map.getWord(wordIndex) : 0L;
        putWord(wordIndex, operator.operation(getWord(wordIndex), rhs));
      }
    } else if (test instanceof BitSetImpl map) {
      long[] values = map.getWords();
      for (int wordIndex = 0; wordIndex < longs; wordIndex++) {
        long rhs = wordIndex < values.length ? values[wordIndex] : 0L;
        putWord(wordIndex, operator.operation(getWord(wordIndex), rhs));
      }
    } else {
      throw new UnsupportedOperationException("Unable to perform bitwise operation from " + test.getClass());
    }
  }

  public int getLongCount() {
    return longs;
  }

  private int checkBoundary(int bit) {
    if (bit < 0 || bit >= capacity) {
      throw new IndexOutOfBoundsException(
          "Expecting range from 0 to " + (capacity - 1) + " received " + bit);
    }
    return bit;
  }

  private void checkRange(int fromIndex, int toIndex) {
    if (fromIndex < 0 || fromIndex > capacity || toIndex < 0 || toIndex > capacity || fromIndex > toIndex) {
      throw new IndexOutOfBoundsException(
          "Expecting range within 0 to " + capacity + " received " + fromIndex + " to " + toIndex);
    }
  }

  private int wordPosition(int wordIndex) {
    if (wordIndex < 0 || wordIndex >= longs) {
      throw new IndexOutOfBoundsException("Invalid long index " + wordIndex);
    }
    return offset + (wordIndex * LONG_SIZE);
  }

  private long getWord(int wordIndex) {
    return backing.getLong(wordPosition(wordIndex));
  }

  private void putWord(int wordIndex, long value) {
    backing.putLong(wordPosition(wordIndex), value);
  }

  @Override
  public Iterator<Integer> iterator() {
    return new BitSetIterator(this);
  }

  @Override
  public ListIterator<Integer> listIterator() {
    return new BitSetListIterator(this);
  }

  long getLong(int index) {
    return getWord(index);
  }
}
