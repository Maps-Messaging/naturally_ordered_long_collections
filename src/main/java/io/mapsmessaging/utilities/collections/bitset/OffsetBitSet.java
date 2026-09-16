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

import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class OffsetBitSet implements Comparable<OffsetBitSet> {

  private static final AtomicLong INSTANCE_ID_GENERATOR = new AtomicLong(0);

  private final long instanceId = INSTANCE_ID_GENERATOR.incrementAndGet();

  protected BitSet rawBitSet;
  @Getter
  protected long start;
  @Getter
  protected long end;
  @Getter
  protected int logicalLength;

  @Getter
  private volatile boolean active = true;

  /**
   * First-close wins. Never overwritten.
   */
  private final AtomicReference<ThreadState> closeState = new AtomicReference<>();

  public OffsetBitSet(@NonNull @NotNull BitSet bitSet, long offset) {
    this(bitSet, offset, representableLength(offset, bitSet.length()));
  }

  public OffsetBitSet(@NonNull @NotNull BitSet bitSet, long offset, int logicalLength) {
    rawBitSet = bitSet;
    configureWindow(offset, logicalLength);
  }

  public void releaseBitSet() {
    ThreadState attempt = ThreadState.capture("releaseBitSet");
    ThreadState existing = closeState.get();
    if (existing == null) {
      if (closeState.compareAndSet(null, attempt)) {
        existing = attempt;
      } else {
        existing = closeState.get();
      }
    }

    if (existing != attempt) {
      PrintStream err = System.err;
      err.println(header("DOUBLE_CLOSE_ATTEMPT", attempt));
      attempt.dumpTo(err);
      err.println(header("ORIGINAL_CLOSE_WAS", existing));
      existing.dumpTo(err);
      err.flush();
    }

    rawBitSet = null;
    active = false;
  }

  public void clear() {
    ensureActive("clear");
    rawBitSet.clear();
  }

  public void clearAll() {
    clear();
  }

  public boolean set(long bit) {
    ensureActive("set");
    return rawBitSet.set(toLocalIndex(bit));
  }

  @Override
  public String toString() {
    if (rawBitSet == null) {
      return "cleared - unusable";
    }
    return "Offset::{Start:" + start + ", End:" + end + ", Length:" + logicalLength + "} > " + rawBitSet;
  }

  public boolean clear(long bit) {
    ensureActive("clear(bit)");
    return rawBitSet.clear(toLocalIndex(bit));
  }

  public boolean isSet(long bit) {
    ensureActive("isSet");
    return rawBitSet.isSet(toLocalIndex(bit));
  }

  public void flip(long bit) {
    ensureActive("flip(bit)");
    rawBitSet.flip(toLocalIndex(bit));
  }

  public void flip(long fromIndex, long toIndex) {
    ensureActive("flip(range)");
    if (fromIndex == toIndex) {
      return;
    }
    if (fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex must not be greater than toIndex");
    }
    int from = toLocalIndex(fromIndex);
    long last = lastValue();
    long maximumExclusive = last == Long.MAX_VALUE ? Long.MAX_VALUE : last + 1;
    if (toIndex < start || toIndex > maximumExclusive) {
      throw new IndexOutOfBoundsException("Range end outside window: " + toIndex);
    }
    int to = (int) (toIndex - start);
    rawBitSet.flip(from, to);
  }

  public int length() {
    ensureActive("length");
    return logicalLength;
  }

  public boolean isEmpty() {
    ensureActive("isEmpty");
    return rawBitSet.isEmpty();
  }

  public int cardinality() {
    ensureActive("cardinality");
    return rawBitSet.cardinality();
  }

  public Long nextSetBit(long fromIndex) {
    ensureActive("nextSetBit");
    Integer index = nextSearchIndex(fromIndex);
    if (index == null) {
      return null;
    }
    int response = rawBitSet.nextSetBit(index);
    return toAbsoluteValue(response);
  }

  public Long nextSetBitAndClear(long fromIndex) {
    ensureActive("nextSetBitAndClear");
    Integer index = nextSearchIndex(fromIndex);
    if (index == null) {
      return null;
    }
    int response = rawBitSet.nextSetBitAndClear(index);
    return toAbsoluteValue(response);
  }

  public Long nextClearBit(long fromIndex) {
    ensureActive("nextClearBit");
    Integer index = nextSearchIndex(fromIndex);
    if (index == null) {
      return null;
    }
    int response = rawBitSet.nextClearBit(index);
    return toAbsoluteValue(response);
  }

  public Long previousSetBit(long fromIndex) {
    ensureActive("previousSetBit");
    Integer index = previousSearchIndex(fromIndex);
    if (index == null) {
      return null;
    }
    int response = rawBitSet.previousSetBit(index);
    return toAbsoluteValue(response);
  }

  public Long previousClearBit(long fromIndex) {
    ensureActive("previousClearBit");
    Integer index = previousSearchIndex(fromIndex);
    if (index == null) {
      return null;
    }
    int response = rawBitSet.previousClearBit(index);
    return toAbsoluteValue(response);
  }

  public void and(BitSet map) {
    ensureActive("and");
    rawBitSet.and(map);
  }

  public void xor(BitSet map) {
    ensureActive("xor");
    rawBitSet.xor(map);
  }

  public void or(BitSet map) {
    ensureActive("or");
    rawBitSet.or(map);
  }

  public void andNot(BitSet map) {
    ensureActive("andNot");
    rawBitSet.andNot(map);
  }

  public @NonNull @NotNull BitSet getBitSet() {
    ensureActive("getBitSet");
    return rawBitSet;
  }

  public void reset(long start, long uniqueId) {
    reset(start, uniqueId, representableLength(start, rawBitSet.length()));
  }

  public void reset(long start, long uniqueId, int logicalLength) {
    ensureActive("reset");
    configureWindow(start, logicalLength);
    rawBitSet.clear();
    rawBitSet.setUniqueId(uniqueId);
  }

  private void configureWindow(long windowStart, int requestedLength) {
    int maximumLength = representableLength(windowStart, rawBitSet.length());
    if (requestedLength <= 0 || requestedLength > maximumLength) {
      throw new IllegalArgumentException(
          "Logical window length must be between 1 and " + maximumLength + ", received " + requestedLength);
    }
    start = windowStart;
    logicalLength = requestedLength;
    long last = lastValue();
    end = last == Long.MAX_VALUE ? Long.MAX_VALUE : last + 1;
  }

  private static int representableLength(long windowStart, int physicalLength) {
    if (physicalLength <= 0) {
      throw new IllegalArgumentException("BitSet length must be greater than 0");
    }
    if (windowStart > Long.MAX_VALUE - (physicalLength - 1L)) {
      return (int) (Long.MAX_VALUE - windowStart + 1L);
    }
    return physicalLength;
  }

  private long lastValue() {
    return start + logicalLength - 1L;
  }

  private int toLocalIndex(long bit) {
    long last = lastValue();
    if (bit < start || bit > last) {
      throw new IndexOutOfBoundsException(
          "Expecting range from " + start + " to " + last + " received " + bit);
    }
    return (int) (bit - start);
  }

  private Integer nextSearchIndex(long fromIndex) {
    if (fromIndex > lastValue()) {
      return null;
    }
    if (fromIndex <= start) {
      return 0;
    }
    return (int) (fromIndex - start);
  }

  private Integer previousSearchIndex(long fromIndex) {
    if (fromIndex < start) {
      return null;
    }
    if (fromIndex >= lastValue()) {
      return logicalLength - 1;
    }
    return (int) (fromIndex - start);
  }

  private Long toAbsoluteValue(int localIndex) {
    if (localIndex < 0 || localIndex >= logicalLength) {
      return null;
    }
    return start + localIndex;
  }

  private void ensureActive(String operation) {
    BitSet local = rawBitSet;
    if (active && local != null) {
      return;
    }

    ThreadState access = ThreadState.capture("access: " + operation);
    ThreadState closedBy = closeState.get();

    PrintStream err = System.err;

    err.println("=========================================================");
    err.println(header("ACCESS_AFTER_CLOSE", access));
    err.println("---------------------------------------------------------");
    access.dumpTo(err);

    if (closedBy != null) {
      long delta = access.timeMillis - closedBy.timeMillis;
      err.println("---------------------------------------------------------");
      err.println("DeltaMillisSinceClose=" + delta);
      err.println(header("CLOSED_BY", closedBy));
      err.println("---------------------------------------------------------");
      closedBy.dumpTo(err);
    } else {
      err.println("---------------------------------------------------------");
      err.println("CLOSED_BY=UNKNOWN (closeState not recorded)");
    }

    err.println("=========================================================");
    err.flush();

    throw new IllegalStateException("BitSet has been released. op=" + operation
        + " instanceId=" + instanceId
        + " identityHash=" + System.identityHashCode(this)
        + " active=" + active
        + " rawBitSetNull=" + (rawBitSet == null));
  }

  private String header(String tag, ThreadState state) {
    BitSet local = rawBitSet;
    int rawIdentity = (local == null) ? 0 : System.identityHashCode(local);

    return "[" + tag + "]"
        + " instanceId=" + instanceId
        + " identityHash=" + System.identityHashCode(this)
        + " active=" + active
        + " rawBitSetNull=" + (local == null)
        + " rawBitSetIdentityHash=" + rawIdentity
        + " start=" + start
        + " end=" + end
        + " thread=" + state.threadName + "(" + state.threadId + ")"
        + " timeMillis=" + state.timeMillis;
  }

  public Iterator<Long> iterator() {
    ensureActive("iterator");
    BitSet snapshot = rawBitSet;
    if (snapshot == null) {
      ensureActive("iterator(snapshot=null)");
    }
    return new OffsetBitSetIterator(snapshot);
  }

  public ListIterator<Long> listIterator() {
    ensureActive("listIterator");
    BitSet snapshot = rawBitSet;
    if (snapshot == null) {
      ensureActive("listIterator(snapshot=null)");
    }
    return new OffsetBitSetListIterator(snapshot);
  }

  @Override
  public int compareTo(OffsetBitSet o) {
    return Long.compare(start, o.start);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof OffsetBitSet offsetBitSet) {
      return compareTo(offsetBitSet) == 0;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(start);
  }

  class OffsetBitSetListIterator implements ListIterator<Long> {

    private final ListIterator<Integer> implIterator;

    OffsetBitSetListIterator(BitSet snapshot) {
      implIterator = snapshot.listIterator();
    }

    public boolean hasNext() {
      return implIterator.hasNext();
    }

    @Override
    public Long next() {
      return start + implIterator.next();
    }

    @Override
    public boolean hasPrevious() {
      return implIterator.hasPrevious();
    }

    @Override
    public Long previous() {
      return start + implIterator.previous();
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
      implIterator.remove();
    }

    @Override
    public void set(Long aLong) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(Long aLong) {
      implIterator.add(toLocalIndex(aLong));
    }
  }

  class OffsetBitSetIterator implements Iterator<Long> {

    private final Iterator<Integer> implIterator;

    OffsetBitSetIterator(BitSet snapshot) {
      implIterator = snapshot.iterator();
    }

    @Override
    public boolean hasNext() {
      return implIterator.hasNext();
    }

    @Override
    public Long next() {
      return start + implIterator.next();
    }

    @Override
    public void remove() {
      implIterator.remove();
    }
  }

  private static final class ThreadState {

    final String threadName;
    final long threadId;
    final long timeMillis;
    final Throwable stackTrace;

    private ThreadState(String threadName, long threadId, long timeMillis, Throwable stackTrace) {
      this.threadName = threadName;
      this.threadId = threadId;
      this.timeMillis = timeMillis;
      this.stackTrace = stackTrace;
    }

    static ThreadState capture(String reason) {
      Thread thread = Thread.currentThread();
      long time = System.currentTimeMillis();
      Throwable trace = new Throwable(reason
          + " thread=" + thread.getName()
          + " id=" + thread.getId()
          + " timeMillis=" + time);
      trace.fillInStackTrace();
      return new ThreadState(thread.getName(), thread.getId(), time, trace);
    }

    void dumpTo(PrintStream err) {
      err.println("Thread:" + threadName + " " + threadId);
      err.println("TimeMillis:" + timeMillis);
      stackTrace.printStackTrace(err);
    }
  }
}
