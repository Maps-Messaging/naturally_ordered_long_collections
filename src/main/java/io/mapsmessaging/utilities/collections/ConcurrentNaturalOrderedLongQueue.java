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
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ConcurrentNaturalOrderedLongQueue extends NaturalOrderedLongQueue {

  private volatile long modificationCount;

  public ConcurrentNaturalOrderedLongQueue() {
    super();
  }

  public ConcurrentNaturalOrderedLongQueue(long id, @NonNull @NotNull BitSetFactory factory) {
    super(id, factory);
  }

  @Override
  public synchronized boolean offer(Long aLong) {
    return super.offer(aLong);
  }

  @Override
  public synchronized Long remove() {
    return super.remove();
  }

  @Override
  public synchronized Long poll() {
    Long result = super.poll();
    if (result != null) {
      modificationCount++;
    }
    return result;
  }

  @Override
  public synchronized Long element() {
    return super.element();
  }

  @Override
  public synchronized Long peek() {
    return super.peek();
  }

  @Override
  public synchronized int size() {
    return super.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    return super.isEmpty();
  }

  @Override
  public synchronized void clear() {
    boolean hadWindows = !tree.isEmpty();
    super.clear();
    if (hadWindows) {
      modificationCount++;
    }
  }

  // From NaturalOrderedCollection
  @Override
  public synchronized boolean add(Long aLong) {
    boolean changed = super.add(aLong);
    if (changed) {
      modificationCount++;
    }
    return changed;
  }

  @Override
  public synchronized boolean remove(Object o) {
    boolean changed = super.remove(o);
    if (changed) {
      modificationCount++;
    }
    return changed;
  }

  @Override
  public synchronized boolean contains(Object o) {
    return super.contains(o);
  }

  @Override
  public synchronized boolean containsAll(@NonNull Collection<?> c) {
    return super.containsAll(c);
  }

  @Override
  public synchronized boolean addAll(@NonNull Collection<? extends Long> c) {
    boolean changed = super.addAll(c);
    if (changed) {
      modificationCount++;
    }
    return changed;
  }

  @Override
  public synchronized boolean removeAll(@NonNull Collection<?> c) {
    boolean changed = super.removeAll(c);
    if (changed) {
      modificationCount++;
    }
    return changed;
  }

  @Override
  public synchronized boolean retainAll(@NonNull Collection<?> c) {
    boolean changed = super.retainAll(c);
    if (changed) {
      modificationCount++;
    }
    return changed;
  }

  @Override
  public synchronized boolean removeIf(Predicate<? super Long> filter) {
    boolean changed = super.removeIf(filter);
    if (changed) {
      modificationCount++;
    }
    return changed;
  }

  @Override
  public synchronized @NotNull Object[] toArray() {
    return super.toArray();
  }

  @Override
  public synchronized <T> T[] toArray(T[] a) {
    return super.toArray(a);
  }

  @Override
  public synchronized void forEach(Consumer<? super Long> action) {
    super.forEach(action);
  }

  @Override
  public synchronized @NotNull Iterator<Long> iterator() {
    Iterator<Long> delegate = super.iterator();
    return new Iterator<>() {
      private long expectedCount = modificationCount;

      private void checkForModification() {
        if (expectedCount != modificationCount) {
          throw new ConcurrentModificationException();
        }
      }

      @Override
      public boolean hasNext() {
        checkForModification();
        return delegate.hasNext();
      }

      @Override
      public Long next() {
        checkForModification();
        return delegate.next();
      }

      @Override
      public void remove() {
        checkForModification();
        delegate.remove();
        expectedCount = modificationCount;
      }
    };
  }

  @Override
  public synchronized void close() {
    boolean hadWindows = !tree.isEmpty();
    super.close();
    if (hadWindows) {
      modificationCount++;
    }
  }

  // Optional: expose unique ID if needed
  @Override
  public synchronized long getUniqueId() {
    return super.getUniqueId();
  }
}
