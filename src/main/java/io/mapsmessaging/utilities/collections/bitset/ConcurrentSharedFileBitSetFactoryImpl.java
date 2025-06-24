/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2025 ] MapsMessaging B.V.
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

import java.io.IOException;
import java.util.List;

public class ConcurrentSharedFileBitSetFactoryImpl extends SharedFileBitSetFactoryImpl {

  public ConcurrentSharedFileBitSetFactoryImpl(@NonNull String baseFilename, int shardCount, int windowSize) throws IOException {
    super(baseFilename, shardCount, windowSize);
  }

  @Override
  public synchronized OffsetBitSet open(long uniqueId, long start) throws IOException {
    return super.open(uniqueId, start);
  }

  @Override
  public synchronized List<OffsetBitSet> get(long uniqueId) {
    return super.get(uniqueId);
  }

  @Override
  public synchronized List<Long> getUniqueIds() {
    return super.getUniqueIds();
  }

  @Override
  public synchronized void release(OffsetBitSet bitSet) {
    super.release(bitSet);
  }

  @Override
  public synchronized void close(OffsetBitSet bitSet) {
    super.close(bitSet);
  }

  @Override
  public synchronized void close() throws IOException {
    super.close();
  }

  @Override
  public synchronized void delete() throws IOException {
    super.delete();
  }

  @Override
  public synchronized List<Long> getAllEventIdsWithInterest() {
    return super.getAllEventIdsWithInterest();
  }
}
