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

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public class NaturalOrderedLongList extends NaturalOrderedCollection implements List<Long> {

  public NaturalOrderedLongList() {
  }

  public NaturalOrderedLongList(long id, BitSetFactory factory) {
    super(id, factory);
  }

  @Override
  public boolean addAll(int index, @NonNull @NotNull Collection<? extends Long> c) {
    throw new UnsupportedOperationException("Index-based operations are not supported by a naturally ordered list");
  }

  @Override
  public @NonNull @NotNull ListIterator<Long> listIterator() {
    return new LongListIterator();
  }

  @Override
  public <T> T[] toArray(IntFunction<T[]> generator) {
    return toArray(generator.apply(size()));
  }


  //<editor-fold desc="Unsupported Index based APIs since this is a natural order list, indexes will change">
  @Override
  public Long get(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Long set(int index, Long element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(int index, Long element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Long remove(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int indexOf(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int lastIndexOf(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull @NotNull ListIterator<Long> listIterator(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull @NotNull List<Long> subList(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }
  //</editor-fold>


  // <editor-fold desc="Standard Iterator">
  class LongListIterator implements ListIterator<Long> {

    // The cursor is the last value before the next forward position.  No bitset
    // iterators are retained, since released file-backed windows can be reused.
    private Long cursor;
    private Long lastReturned;

    private Long nextValue() {
      if (cursor != null && cursor == Long.MAX_VALUE) {
        return null;
      }
      long from = cursor == null ? Long.MIN_VALUE : cursor + 1L;
      for (OffsetBitSet window : tree.tailMap(factory.getStartIndex(from), true).values()) {
        Long value = window.nextSetBit(from);
        if (value != null) {
          return value;
        }
      }
      return null;
    }

    private Long previousValue(Long from) {
      if (from == null) {
        return null;
      }
      for (OffsetBitSet window : tree.headMap(factory.getStartIndex(from), true).descendingMap().values()) {
        Long value = window.previousSetBit(from);
        if (value != null) {
          return value;
        }
      }
      return null;
    }

    @Override
    public boolean hasNext() {
      return nextValue() != null;
    }

    @Override
    public Long next() {
      Long value = nextValue();
      if (value == null) {
        throw new NoSuchElementException();
      }
      cursor = value;
      lastReturned = value;
      return value;
    }

    @Override
    public boolean hasPrevious() {
      return previousValue(cursor) != null;
    }

    @Override
    public Long previous() {
      Long value = previousValue(cursor);
      if (value == null) {
        throw new NoSuchElementException();
      }
      cursor = value == Long.MIN_VALUE ? null : previousValue(value - 1L);
      lastReturned = value;
      return value;
    }

    @Override
    public int nextIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int previousIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void remove() {
      if (lastReturned == null) {
        throw new IllegalStateException("next()/previous() has not been called or the element has already been removed");
      }
      NaturalOrderedLongList.this.remove(lastReturned);
      lastReturned = null;
    }

    @Override
    public void set(Long aLong) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(Long aLong) {
      NaturalOrderedLongList.this.add(aLong);
      cursor = aLong;
      lastReturned = null;
    }

    @Override
    public void forEachRemaining(Consumer<? super Long> action) {
      Objects.requireNonNull(action);
      while (hasNext()) {
        action.accept(next());
      }
    }
}
