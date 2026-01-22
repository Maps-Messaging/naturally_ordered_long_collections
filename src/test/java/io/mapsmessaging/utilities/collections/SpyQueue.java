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

import java.util.Queue;

public class SpyQueue<T> implements Queue<T> {

  private final Queue<T> delegate;
  private int isEmptyCalls;
  private int peekCalls;

  SpyQueue(Queue<T> delegate) {
    this.delegate = delegate;
  }

  int getIsEmptyCalls() {
    return isEmptyCalls;
  }

  int getPeekCalls() {
    return peekCalls;
  }

  @Override
  public boolean isEmpty() {
    isEmptyCalls++;
    return delegate.isEmpty();
  }

  @Override
  public T peek() {
    peekCalls++;
    return delegate.peek();
  }

  // --- delegate the rest ---
  @Override public int size() { return delegate.size(); }
  @Override public boolean contains(Object o) { return delegate.contains(o); }
  @Override public java.util.Iterator<T> iterator() { return delegate.iterator(); }
  @Override public Object[] toArray() { return delegate.toArray(); }
  @Override public <T1> T1[] toArray(T1[] a) { return delegate.toArray(a); }
  @Override public boolean add(T t) { return delegate.add(t); }
  @Override public boolean remove(Object o) { return delegate.remove(o); }
  @Override public boolean containsAll(java.util.Collection<?> c) { return delegate.containsAll(c); }
  @Override public boolean addAll(java.util.Collection<? extends T> c) { return delegate.addAll(c); }
  @Override public boolean removeAll(java.util.Collection<?> c) { return delegate.removeAll(c); }
  @Override public boolean retainAll(java.util.Collection<?> c) { return delegate.retainAll(c); }
  @Override public void clear() { delegate.clear(); }
  @Override public boolean offer(T t) { return delegate.offer(t); }
  @Override public T remove() { return delegate.remove(); }
  @Override public T poll() { return delegate.poll(); }
  @Override public T element() { return delegate.element(); }
}