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

    ArrayList<ListIterator<Long>> iterators;
    int iteratorIndex;
    ListIterator<Long> active;
    private Long lastReturned;

    LongListIterator() {
      iterators = new ArrayList<>();
      for (OffsetBitSet bitMap : tree.values()) {
        iterators.add(bitMap.listIterator());
      }
      if (iterators.isEmpty()) {
        active = null;
        iteratorIndex = -1;
      } else {
        active = iterators.get(0);
        iteratorIndex = 0;
      }
    }

    @Override
    public boolean hasNext() {
      if (active == null) {
        return false;
      }
      if (active.hasNext()) {
        return true;
      } else {
        iteratorIndex++;
        if (iteratorIndex >= iterators.size()) {
          active = null;
          return false;
        }
        active = iterators.get(iteratorIndex);
        return hasNext();
      }
    }

    @Override
    public Long next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      lastReturned = active.next();
      return lastReturned;
    }

    @Override
    public boolean hasPrevious() {
      if (active == null) {
        if (iteratorIndex >= iterators.size()) {
          iteratorIndex = iterators.size() - 1;
        }
        if (iteratorIndex < 0) {
          return false;
        }
        active = iterators.get(iteratorIndex);
      }
      if (active.hasPrevious()) {
        return true;
      } else {
        iteratorIndex--;
        if (iteratorIndex < 0) {
          active = null;
          return false;
        }
        active = iterators.get(iteratorIndex);
        return hasPrevious();
      }
    }

    @Override
    public Long previous() {
      if (!hasPrevious()) {
        throw new NoSuchElementException();
      }
      lastReturned = active.previous();
      return lastReturned;
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
      boolean atEnd = active == null && (iteratorIndex >= iterators.size() || iterators.isEmpty());
      OffsetBitSet lastWindow = atEnd && !tree.isEmpty() ? tree.lastEntry().getValue() : null;
      NaturalOrderedLongList.this.add(aLong);
      lastReturned = null;

      if (atEnd) {
        OffsetBitSet insertedWindow = tree.get(factory.getStartIndex(aLong));
        if (insertedWindow == lastWindow && !iterators.isEmpty()) {
          active = iterators.get(iterators.size() - 1);
        } else if (insertedWindow != null &&
            (lastWindow == null || insertedWindow.getStart() > lastWindow.getStart())) {
          active = insertedWindow.listIterator();
          iterators.add(active);
        }
        if (active != null) {
          iteratorIndex = iterators.size() - 1;
          active.add(aLong);
        }
      }
    }

    @Override
    public void forEachRemaining(Consumer<? super Long> action) {
      Objects.requireNonNull(action);
      while (hasNext())
        action.accept(next());
    }
  }
}
