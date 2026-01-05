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
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileOffsetBitSet extends OffsetBitSet implements Closeable {

  private final BitSetFactory factory;
  @Getter
  private final long position;
  @Getter
  private final int shardId;

  @Getter
  @Setter
  private boolean allocated;

  public FileOffsetBitSet(@NonNull @NotNull ByteBufferBackedBitMap bitSet, long position, long offset, @NonNull @NotNull BitSetFactory factory, int shardId) {
    super(bitSet, offset);
    this.factory = factory;
    this.position = position;
    this.shardId = shardId;
    allocated = false;
  }

  public long getUniqueId() {
    return getBitSet().getUniqueId();
  }

  @Override
  public void close() {
    allocated = false;
    factory.release(this);
  }

  @Override
  public void reset(long start, long uniqueId) {
    if (uniqueId != -1 && allocated) {
      Logger.getLogger(FileOffsetBitSet.class.getName()).log(Level.ALL, "Resetting offset bitset {0} on a currently allocated bitset", uniqueId);
    }
    allocated = uniqueId != -1;
    super.reset(start, uniqueId);
  }


  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    FileOffsetBitSet other = (FileOffsetBitSet) obj;
    return this.position == other.position && this.getShardId() == other.getShardId();
  }

  @Override
  public int hashCode() {
    return Long.hashCode(position) * 31 + Long.hashCode(getShardId());
  }
}