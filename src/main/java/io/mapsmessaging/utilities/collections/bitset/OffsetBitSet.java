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
  private volatile boolean active = true;

  /**
   * First-close wins. Never overwritten.
   */
  private final AtomicReference<ThreadState> closeState = new AtomicReference<>();

  public OffsetBitSet(@NonNull @NotNull BitSet bitSet, long offset) {
    rawBitSet = bitSet;
    this.start = offset;
    end = start + rawBitSet.length();
  }

  public void releaseBitSet() {
    // Record this close attempt (even if it's a double close, we want the stack).
    ThreadState attempt = ThreadState.capture("releaseBitSet");

    // First close wins.
    ThreadState existing = closeState.get();
    if (existing == null) {
      if (closeState.compareAndSet(null, attempt)) {
        existing = attempt;
      } else {
        existing = closeState.get();
      }
    }

    // If this isn't the first close, scream loudly with both stacks.
    if (existing != attempt) {
      PrintStream err = System.err;
      err.println(header("DOUBLE_CLOSE_ATTEMPT", attempt));
      attempt.dumpTo(err);
      err.println(header("ORIGINAL_CLOSE_WAS", existing));
      existing.dumpTo(err);
      err.flush();
    }

    // Make the state clearly "dead".
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
    return rawBitSet.set((int) (bit - start));
  }

  @Override
  public String toString() {
    if (rawBitSet == null) {
      return "cleared - unusable";
    }
    return "Offset::{Start:" + start + ", End:" + end + "} > " + rawBitSet;
  }

  public boolean clear(long bit) {
    ensureActive("clear(bit)");
    return rawBitSet.clear((int) (bit - start));
  }

  public boolean isSet(long bit) {
    ensureActive("isSet");
    return rawBitSet.isSet((int) (bit - start));
  }

  public void flip(long bit) {
    ensureActive("flip(bit)");
    rawBitSet.flip((int) (bit - start));
  }

  public void flip(long fromIndex, long toIndex) {
    ensureActive("flip(range)");
    rawBitSet.flip((int) (fromIndex - start), (int) (toIndex - start));
  }

  public int length() {
    ensureActive("length");
    return rawBitSet.length();
  }

  public boolean isEmpty() {
    ensureActive("isEmpty");
    return rawBitSet.isEmpty();
  }

  public int cardinality() {
    ensureActive("cardinality");
    return rawBitSet.cardinality();
  }

  public long nextSetBit(long fromIndex) {
    ensureActive("nextSetBit");
    int index = (int) (fromIndex - start);
    long response = rawBitSet.nextSetBit(index);
    if (response >= 0) {
      response += start;
    }
    return response;
  }

  public long nextSetBitAndClear(long fromIndex) {
    ensureActive("nextSetBitAndClear");
    long response = rawBitSet.nextSetBitAndClear((int) (fromIndex - start));
    if (response >= 0) {
      response += start;
    }
    return response;
  }

  public long nextClearBit(long fromIndex) {
    ensureActive("nextClearBit");
    return rawBitSet.nextClearBit((int) (fromIndex - start)) + start;
  }

  public long previousSetBit(long fromIndex) {
    ensureActive("previousSetBit");
    return rawBitSet.previousSetBit((int) (fromIndex - start)) + start;
  }

  public long previousClearBit(long fromIndex) {
    ensureActive("previousClearBit");
    return rawBitSet.previousClearBit((int) (fromIndex - start)) + start;
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
    ensureActive("reset");
    this.start = start;
    end = start + rawBitSet.length();
    rawBitSet.clear();
    rawBitSet.setUniqueId(uniqueId);
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
    long v = (start - o.start);
    if (v < 0) {
      return -1;
    }
    if (v > 0) {
      return 1;
    }
    return 0;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof OffsetBitSet offsetBitSet) {
      return compareTo(offsetBitSet) == 0;
    }
    return super.equals(obj);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
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
      implIterator.add((int) (aLong - start));
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
