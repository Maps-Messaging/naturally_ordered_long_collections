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
import org.jetbrains.annotations.NotNull;

import java.util.ListIterator;
import java.util.NoSuchElementException;

class BitSetListIterator implements ListIterator<Integer> {

  private final BitSet active;
  private int current;
  private int selected;

  public BitSetListIterator(@NonNull @NotNull BitSet bitSet) {
    active = bitSet;
    current = 0;
    selected = -1;
  }

  @Override
  public boolean hasNext() {
    return active.nextSetBit(current) >= 0;
  }

  @Override
  public Integer next() {
    int nextBit = active.nextSetBit(current);
    if (nextBit < 0) {
      throw new NoSuchElementException();
    }
    selected = nextBit;
    current = nextBit + 1;
    return nextBit;
  }

  @Override
  public boolean hasPrevious() {
    return current > 0 && active.previousSetBit(current - 1) >= 0;
  }

  @Override
  public Integer previous() {
    if (current <= 0) {
      throw new NoSuchElementException();
    }
    int previousBit = active.previousSetBit(current - 1);
    if (previousBit < 0) {
      throw new NoSuchElementException();
    }
    selected = previousBit;
    current = previousBit;
    return previousBit;
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
    if (selected < 0) {
      throw new IllegalStateException("next()/previous() has not been called or entry already removed");
    }
    active.clear(selected);
    selected = -1;
  }

  @Override
  public void set(Integer integer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(Integer integer) {
    active.set(integer);
  }
}
