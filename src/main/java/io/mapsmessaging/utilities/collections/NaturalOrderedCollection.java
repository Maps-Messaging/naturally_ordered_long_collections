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

package io.mapsmessaging.utilities.collections;

import io.mapsmessaging.utilities.collections.bitset.BitSetFactory;
import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class NaturalOrderedCollection implements Collection<Long> {

  protected final TreeMap<Long, OffsetBitSet> tree;
  protected final BitSetFactory factory;
  private final int size;

  @Getter
  private final int uniqueId;


  public NaturalOrderedCollection() {
    this(0, new BitSetFactoryImpl(8192));
  }

  public NaturalOrderedCollection(int id, @NonNull BitSetFactory factory) {
    tree = new TreeMap<>(new OffsetBitSetComparator());
    this.factory = factory;
    this.size = factory.getSize();
    uniqueId = id;
    List<OffsetBitSet> reloadList = factory.get(id);
    for (OffsetBitSet bitset : reloadList) {
      tree.put(bitset.getStart(), bitset);
    }
  }

  public void close() {
    for (OffsetBitSet offsetBitSet : tree.values()) {
      factory.close(offsetBitSet);
    }
    tree.clear();
  }

  @Override
  public int size() {
    var counter = 0;
    for (OffsetBitSet bitMap : tree.values()) {
      counter += bitMap.cardinality();
    }
    return counter;
  }

  private OffsetBitSet locate(long value) {
    long start = (value / size) * size;
    return tree.get(start);
  }

  @Override
  public boolean isEmpty() {
    if (tree.isEmpty()) {
      return true;
    }
    for (OffsetBitSet bitMap : tree.values()) {
      if (!bitMap.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public @NonNull @NotNull Iterator<Long> iterator() {
    return new LongIterator();
  }

  @Override
  public Object[] toArray() {
    Collection<OffsetBitSet> bitsets = tree.values();
    List<Long> response = new ArrayList<>();
    for(OffsetBitSet bitSet:bitsets){
      Iterator<Long> itr = bitSet.iterator();
      while(itr.hasNext()){
        response.add(itr.next());
      }
    }
    return response.toArray();
  }

  @Override
  public boolean contains(Object o) {
    if (o instanceof Long) {
      long lookup = (Long) o;
      OffsetBitSet active = locate(lookup);
      if (active != null) {
        return active.isSet(lookup);
      }
    }
    return false;
  }

  @Override
  public void forEach(Consumer<? super Long> action) {
    Objects.requireNonNull(action);
    for (Long aLong : this) {
      action.accept(aLong);
    }
  }

  @Override
  public <T> T[] toArray(T[] a) {
    Iterator<Long> itr = iterator();
    for (var x = 0; x < a.length && itr.hasNext(); x++) {
      a[x] = (T) itr.next();
    }
    return a;
  }

  @Override
  public boolean add(Long aLong) {
    OffsetBitSet active = locate(aLong);
    if (active == null) {
      try {
        active = factory.open(uniqueId, aLong);
      } catch (IOException e) {
        throw new IORunTimeException("Fatal error opening new bitset, unable to continue", e);
      }
      tree.put(active.getStart(), active);
    }
    return active.set(aLong);
  }

  @Override
  public boolean remove(Object o) {
    if (o instanceof Long) {
      long value = (Long) o;
      OffsetBitSet active = locate(value);
      if (active != null) {
        boolean result = active.clear(value);
        if (result && active.isEmpty()) {
          tree.remove(active.getStart());
          factory.release(active);
        }
        return result;
      }
    }
    return false;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    for (Object l : c) {
      if (l instanceof Long) {
        long test = (Long) l;
        if (!contains(test)) {
          return false;
        }
      } else {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean addAll(@NonNull @NotNull Collection<? extends Long> c) {
    if (isMatching(c)) {
      NaturalOrderedCollection rhs = (NaturalOrderedCollection)c;
      Collection<OffsetBitSet> bitsets = rhs.tree.values();
      for(OffsetBitSet toAddBitset:bitsets){
        OffsetBitSet copy = tree.get(toAddBitset.getStart());
        if(copy == null){
          try {
            copy = factory.open(uniqueId, toAddBitset.getStart());
            tree.put(copy.getStart(), copy);
          } catch (IOException e) {
            throw new IORunTimeException("Fatal error opening new bitset, unable to continue", e);
          }
        }
        copy.getBitSet().or(toAddBitset.getBitSet());
      }
    }
    else {
      for (long value : c) {
        add(value);
      }
    }
    return true;
  }

  @Override
  public boolean removeAll(@NonNull @NotNull Collection<?> c) {
    if (isMatching(c)) {
      NaturalOrderedCollection rhs = (NaturalOrderedCollection) c;
      Collection<OffsetBitSet> bitsets = rhs.tree.values();
      for (OffsetBitSet toRemove : bitsets) {
        OffsetBitSet copy = tree.get(toRemove.getStart());
        if (copy != null) {
          copy.getBitSet().andNot(toRemove.getBitSet());
          if(copy.isEmpty()){
            tree.remove(copy.getStart());
            factory.release(copy);
          }
        }
      }
    }
    else {
      for (Object value : c) {
        remove(value);
      }
    }
    return true;
  }

  @Override
  public boolean removeIf(Predicate<? super Long> filter) {
    Objects.requireNonNull(filter);
    Iterator<Long> itr = new LongIterator();
    while (itr.hasNext()) {
      if (filter.test(itr.next())) {
        itr.remove();
      }
    }
    return false;
  }

  @Override
  public boolean retainAll(@NonNull @NotNull Collection<?> c) {
    var changed = false;
    if (isMatching(c)) {
      changed = matchingRetainAll(c);
    }
    else {
      changed = nonMatchingRetainAll(c);
    }
    return changed;
  }

  private boolean nonMatchingRetainAll(Collection<?> c){
    boolean changed = false;
    Iterator<Long> itr = this.iterator();
    while (itr.hasNext()) {
      long val = itr.next();
      if (!c.contains(val)) {
        itr.remove();
        changed = true;
      }
    }
    return changed;
  }

  private boolean matchingRetainAll(Collection<?> c){
    boolean changed = false;
    NaturalOrderedCollection rhs = (NaturalOrderedCollection) c;
    Collection<OffsetBitSet> bitsets = rhs.tree.values();
    for (OffsetBitSet toRetain : bitsets) {
      OffsetBitSet copy = tree.get(toRetain.getStart());
      if (copy != null) {
        int original = copy.cardinality();
        copy.getBitSet().and(toRetain.getBitSet());
        if(copy.isEmpty()){
          tree.remove(copy.getStart());
          factory.release(copy);
        }
        else{
          changed = original != copy.cardinality() || changed;
        }
      }
    }
    return changed;
  }

  @Override
  public void clear() {
    for (OffsetBitSet bitMap : tree.values()) {
      bitMap.clearAll();
    }
  }

  public String toString() {
    return "size = " + size();
  }

  protected boolean isMatching(Collection<?> c){
    return (c instanceof NaturalOrderedCollection && ((NaturalOrderedCollection)c).factory.getSize() == factory.getSize());
  }

  // </editor-fold>
  static class OffsetBitSetComparator implements Comparator<Long> {

    @Override
    public int compare(Long o1, Long o2) {
      return Long.compare(o1, o2);
    }
  }

  // <editor-fold desc="Standard Iterator">
  class LongIterator implements Iterator<Long> {

    ArrayList<Iterator<Long>> iterators;
    Iterator<Long> active;

    LongIterator() {
      iterators = new ArrayList<>();
      for (OffsetBitSet bitMap : tree.values()) {
        iterators.add(bitMap.iterator());
      }
    }

    @Override
    public boolean hasNext() {
      if (active == null) {
        if (!iterators.isEmpty()) {
          active = iterators.remove(0);
        } else {
          return false;
        }
      }
      if (active.hasNext()) {
        return true;
      } else {
        active = null;
        return hasNext();
      }
    }

    @Override
    public Long next() {
      if (active != null) {
        return active.next();
      }
      return null;
    }

    @Override
    public void remove() {
      active.remove();
    }

    @Override
    public void forEachRemaining(Consumer<? super Long> action) {
      Objects.requireNonNull(action);
      while (hasNext()) {
        action.accept(next());
      }
    }
  }

  public static final class IORunTimeException extends RuntimeException{
    public IORunTimeException(String s, IOException e) {
      super(s, e);
    }
  }
}
