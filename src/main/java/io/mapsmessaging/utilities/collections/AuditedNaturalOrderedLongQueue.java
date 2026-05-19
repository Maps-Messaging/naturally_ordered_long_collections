/*
 *  Copyright [ 2020 - 2024 ] [Matthew Buckton]
 *  Copyright [ 2024 - 2026 ] [Maps Messaging B.V.]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.mapsmessaging.utilities.collections;

import io.mapsmessaging.utilities.collections.bitset.BitSetFactory;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Auditing shim: delegates to the concrete implementation but logs every call.
 *
 * Log format: one JSON object per line (JSONL).
 * This is intentionally simple to parse/replay.
 */
public class AuditedNaturalOrderedLongQueue extends NaturalOrderedLongQueue implements Closeable {

  private final AuditWriter auditWriter;

  public AuditedNaturalOrderedLongQueue() {
    super();
    int hash = System.identityHashCode(this);
    File file = new File("AuditedNaturalOrderedLongQueue_"+hash+".dump");
    auditWriter = new AuditWriter(file.toPath());
    auditWriter.writeEvent("constructor", "()", "ok", null);
  }

  public AuditedNaturalOrderedLongQueue(long id, @NonNull @NotNull BitSetFactory factory) {
    super(id, factory);
    int hash = System.identityHashCode(this);
    File file = new File("AuditedNaturalOrderedLongQueue_"+hash+".dump");
    auditWriter = new AuditWriter(file.toPath());
    auditWriter.writeEvent("constructor", "(id=" + id + ")", "ok", null);
  }

  @Override
  public synchronized boolean offer(Long value) {
    String args = "(value=" + value + ")";
    try {
      boolean result = super.offer(value);
      auditWriter.writeEvent("offer", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("offer", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized Long remove() {
    String args = "()";
    try {
      Long result = super.remove();
      auditWriter.writeEvent("remove", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("remove", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized Long poll() {
    String args = "()";
    try {
      Long result = super.poll();
      auditWriter.writeEvent("poll", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("poll", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized Long element() {
    String args = "()";
    try {
      Long result = super.element();
      auditWriter.writeEvent("element", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("element", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized Long peek() {
    String args = "()";
    try {
      Long result = super.peek();
      auditWriter.writeEvent("peek", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("peek", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized int size() {
    String args = "()";
    try {
      int result = super.size();
      auditWriter.writeEvent("size", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("size", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean isEmpty() {
    String args = "()";
    try {
      boolean result = super.isEmpty();
      auditWriter.writeEvent("isEmpty", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("isEmpty", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized void clear() {
    String args = "()";
    try {
      super.clear();
      auditWriter.writeEvent("clear", args, "ok", null);
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("clear", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean add(Long value) {
    String args = "(value=" + value + ")";
    try {
      boolean result = super.add(value);
      auditWriter.writeEvent("add", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("add", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean remove(Object value) {
    String args = "(value=" + value + ")";
    try {
      boolean result = super.remove(value);
      auditWriter.writeEvent("remove(Object)", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("remove(Object)", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean contains(Object value) {
    String args = "(value=" + value + ")";
    try {
      boolean result = super.contains(value);
      auditWriter.writeEvent("contains", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("contains", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean containsAll(@NonNull Collection<?> collection) {
    Objects.requireNonNull(collection, "collection");
    String args = "(collectionSize=" + collection.size() + ")";
    try {
      boolean result = super.containsAll(collection);
      auditWriter.writeEvent("containsAll", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("containsAll", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean addAll(@NonNull Collection<? extends Long> collection) {
    Objects.requireNonNull(collection, "collection");
    String args = "(collectionSize=" + collection.size() + ")";
    try {
      boolean result = super.addAll(collection);
      auditWriter.writeEvent("addAll", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("addAll", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean removeAll(@NonNull Collection<?> collection) {
    Objects.requireNonNull(collection, "collection");
    String args = "(collectionSize=" + collection.size() + ")";
    try {
      boolean result = super.removeAll(collection);
      auditWriter.writeEvent("removeAll", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("removeAll", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean retainAll(@NonNull Collection<?> collection) {
    Objects.requireNonNull(collection, "collection");
    String args = "(collectionSize=" + collection.size() + ")";
    try {
      boolean result = super.retainAll(collection);
      auditWriter.writeEvent("retainAll", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("retainAll", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized boolean removeIf(Predicate<? super Long> filter) {
    Objects.requireNonNull(filter, "filter");
    String args = "(filter=" + filter.getClass().getName() + ")";
    try {
      boolean result = super.removeIf(filter);
      auditWriter.writeEvent("removeIf", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("removeIf", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized @NotNull Object[] toArray() {
    String args = "()";
    try {
      Object[] result = super.toArray();
      auditWriter.writeEvent("toArray", args, "length=" + result.length, null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("toArray", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized <T> T[] toArray(T[] array) {
    Objects.requireNonNull(array, "array");
    String args = "(arrayLength=" + array.length + ")";
    try {
      T[] result = super.toArray(array);
      auditWriter.writeEvent("toArray(T[])", args, "length=" + result.length, null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("toArray(T[])", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized void forEach(Consumer<? super Long> action) {
    Objects.requireNonNull(action, "action");
    String args = "(action=" + action.getClass().getName() + ")";
    try {
      super.forEach(action);
      auditWriter.writeEvent("forEach", args, "ok", null);
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("forEach", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized @NotNull Iterator<Long> iterator() {
    String args = "()";
    try {
      Iterator<Long> result = super.iterator();
      auditWriter.writeEvent("iterator", args, "ok", null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("iterator", args, null, exception);
      throw exception;
    }
  }

  @Override
  public synchronized void close() {
    String args = "()";
    try {
      super.close();
      auditWriter.writeEvent("close", args, "ok", null);
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("close", args, null, exception);
      throw exception;
    } finally {
      auditWriter.close();
    }
  }

  @Override
  public synchronized long getUniqueId() {
    String args = "()";
    try {
      long result = super.getUniqueId();
      auditWriter.writeEvent("getUniqueId", args, String.valueOf(result), null);
      return result;
    } catch (RuntimeException exception) {
      auditWriter.writeEvent("getUniqueId", args, null, exception);
      throw exception;
    }
  }
}

