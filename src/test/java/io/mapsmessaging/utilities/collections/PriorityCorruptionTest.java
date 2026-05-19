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

import io.mapsmessaging.utilities.collections.bitset.SharedFileBitSetFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

class PriorityCorruptionTest {

  private static final int PRIORITY_BITS = 32 - Integer.numberOfLeadingZeros(10); // e.g., 4 for value 10
  private static final int PRIORITY_SHIFT = Long.SIZE - PRIORITY_BITS; // e.g., 64 - 4 = 60
  private static final long PRIORITY_MASK = (1L << PRIORITY_BITS) - 1; // e.g., 0b1111
  private static final long BASE_ID_MASK = (1L << PRIORITY_SHIFT) - 1; // lower bits only

  @Test
  void testCorruption() throws IOException, InterruptedException {

    long baseUniqueId = 1L;
    SharedFileBitSetFactoryImpl sharedFileBitSetFactory =
        new SharedFileBitSetFactoryImpl("./testCorruption.bin", 4, 4096 );
    PriorityQueue<Long> queue1 = createQueue(sharedFileBitSetFactory, baseUniqueId);
    for(long x = 0; x < 10; x++){
      queue1.add(x, 1);
    }
    PriorityQueue<Long> queue2 = createQueue(sharedFileBitSetFactory, baseUniqueId);


    Queue<Long> queue3 = new LinkedList<>();
    queue1.flatten(queue3);
    long val = 0;
    for(Long x:queue3){
      Assertions.assertEquals(val, x);
      val++;
    }

    queue3 = new LinkedList<>();
    queue2.flatten(queue3);
    val = 0;
    for(Long x:queue3){
      Assertions.assertEquals(val, x);
      val++;
    }

    while(!queue1.isEmpty()){
      queue1.poll();
    }
    queue1.clear();
    Thread.sleep(11_000L);
    queue2.clear();
  }

  private PriorityQueue<Long> createQueue(SharedFileBitSetFactoryImpl  sharedFileBitSetFactory, long id) {
    NaturalOrderedLongQueue[] priorityLists = new NaturalOrderedLongQueue[10];
    for (int x = 0; x < priorityLists.length; x++) {
      long priorityId = compute(id, x);
      priorityLists[x] = new NaturalOrderedLongQueue(priorityId, sharedFileBitSetFactory);
    }
    return new PriorityQueue<>(priorityLists, null);
  }

  public static long compute(long baseId, int priority) {
    if ((priority & ~0x0F) != 0) {
      throw new IllegalArgumentException("Priority must be between 0 and 15");
    }
    return ((long) priority << PRIORITY_SHIFT) | (baseId & BASE_ID_MASK);
  }
}
