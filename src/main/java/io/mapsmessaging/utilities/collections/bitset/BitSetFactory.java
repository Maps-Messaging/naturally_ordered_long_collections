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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public abstract class BitSetFactory implements Closeable {

  protected final int windowSize;

  protected BitSetFactory(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("Window size must be greater than 0");
    }
    windowSize = size;
  }

  public void close() throws IOException {
    // Nothing required to clean up any resources
  }

  public void delete() throws IOException {
    // Nothing required to clean up any resources
  }

  public int getSize() {
    return windowSize;
  }

  public long getStartIndex(long id) {
    long remainder = Math.floorMod(id, (long) windowSize);
    if (remainder == 0) {
      return id;
    }
    if (id < Long.MIN_VALUE + remainder) {
      return Long.MIN_VALUE;
    }
    return id - remainder;
  }

  public int getWindowLength(long start) {
    if (start == Long.MIN_VALUE) {
      long remainder = Math.floorMod(Long.MIN_VALUE, (long) windowSize);
      if (remainder != 0) {
        return (int) (windowSize - remainder);
      }
    }
    if (start > Long.MAX_VALUE - (windowSize - 1L)) {
      return (int) (Long.MAX_VALUE - start + 1L);
    }
    return windowSize;
  }

  public List<OffsetBitSet> getFreeBitSets() {
    return List.of();
  }

  public abstract void close(OffsetBitSet bitset);

  public abstract void release(OffsetBitSet bitset);

  public abstract OffsetBitSet open(long uniqueId, long offset) throws IOException;

  public abstract List<OffsetBitSet> get(long uniqueId);

  public abstract List<Long> getUniqueIds();
}
