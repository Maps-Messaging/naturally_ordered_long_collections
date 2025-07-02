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

import io.mapsmessaging.utilities.collections.NaturalOrderedLongList;
import lombok.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SharedFileBitSetFactoryImpl extends BitSetFactory {

  private final FileBitSetFactoryImpl[] shards;
  private final int shardCount;

  public SharedFileBitSetFactoryImpl(@NonNull String baseFilename, int shardCount, int windowSize) throws IOException {
    super(windowSize);
    if(shardCount <= 0){
      throw new IllegalArgumentException("shardCount must be greater than 0");
    }
    this.shardCount = shardCount;
    this.shards = new FileBitSetFactoryImpl[shardCount];

    for (int i = 0; i < shardCount; i++) {
      String shardFile = baseFilename + "_" + i;
      shards[i] = new FileBitSetFactoryImpl(shardFile, windowSize, i);
    }
  }

  @Override
  public OffsetBitSet open(long uniqueId, long start) throws IOException {
    return selectShard(uniqueId).open(uniqueId, start);
  }

  @Override
  public List<OffsetBitSet> get(long uniqueId) {
    if(uniqueId < 0){
      List<OffsetBitSet> bitSets = new ArrayList<>();
      for (var shard : shards) {
        bitSets.addAll(shard.get(-1));
      }
      return bitSets;
    }
    return selectShard(uniqueId).get(uniqueId);
  }


  @Override
  public List<Long> getUniqueIds() {
    List<Long> result = new ArrayList<>();
    for (var shard : shards) {
      result.addAll(shard.getUniqueIds());
    }
    return result;
  }

  @Override
  public void release(OffsetBitSet bitSet) {
    FileBitSetFactoryImpl fileBitSetFactory = selectShard(( (FileOffsetBitSet)  bitSet).getUniqueId());
    if(fileBitSetFactory.getShard() != ((FileOffsetBitSet) bitSet).getShardId()){
      System.err.println("Wrong shard selected "+fileBitSetFactory.getShard()+" should be "+fileBitSetFactory.getShard());
    }
    fileBitSetFactory.release(bitSet);
  }

  @Override
  public void close(OffsetBitSet bitSet) {
    selectShard(( (FileOffsetBitSet)  bitSet).getUniqueId()).close(bitSet);
  }

  @Override
  public void close() throws IOException {
    for (var shard : shards) {
      shard.close();
    }
  }

  @Override
  public void delete() throws IOException {
    for(var shard : shards){
      shard.delete();
    }
  }

  public List<Long> getAllEventIdsWithInterest() {
    NaturalOrderedLongList result = new NaturalOrderedLongList();
    for (FileBitSetFactoryImpl shard : shards) {
      for (long uniqueId : shard.getUniqueIds()) {
        List<OffsetBitSet> bitSets = shard.get(uniqueId);
        for (OffsetBitSet bitSet : bitSets) {
          Iterator<Long> iterator = bitSet.iterator();
          while(iterator.hasNext()){
            result.add(iterator.next());
          }
        }
      }
    }
    return result;
  }

  private FileBitSetFactoryImpl selectShard(long uniqueId) {
    return shards[Math.floorMod(uniqueId, shardCount)];
  }

}