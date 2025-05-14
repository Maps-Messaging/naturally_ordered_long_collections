/*
 *  Copyright [ 2020 - 2024 ] [Matthew Buckton]
 *  Copyright [ 2024 - 2025 ] [Maps Messaging B.V.]
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
import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import org.openjdk.jmh.annotations.*;

import java.util.Queue;
import java.util.Random;

@State(Scope.Benchmark)
public class PriorityBitSetQueueJMH {

  Random random = new Random(System.nanoTime());
  PriorityQueue<Long> priorityQueue;
  int count =0;
  long lastEntry =0;

  @Param({ "true", "false"})
  boolean bufferBased;

  @Setup
  public void createState(){
    priorityQueue = createQueue(16);
  }

  @TearDown
  public void cleanUp() throws Exception {
    priorityQueue.close();
  }

  public PriorityQueue<Long> createQueue(int priorities){
    Queue<Long>[] external = new Queue[priorities];
    for(int x=0;x<priorities;x++){
      BitSetFactory factory;
      if(bufferBased){
        factory = new ByteBufferBitSetFactoryImpl(4096);
      }
      else{
        factory = new BitSetFactoryImpl(4096);
      }
      external[x] = new NaturalOrderedLongQueue(x,factory);
    }
    return new PriorityQueue<>(external,null);
  }

  @Fork(value = 2, warmups = 2)
  @Warmup(iterations = 2)
  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  public void addAndRemoveLinear(){
    int priority = ((count++) % 16);
    long entry = lastEntry++;
    priorityQueue.add(entry, priority);
    priorityQueue.poll();
  }

  @Fork(value = 2, warmups = 2)
  @Warmup(iterations = 2)
  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  public void addAndRemoveRandom(){
    int priority = ((count++) % 16);
    long entry = Math.abs(random.nextLong());
    priorityQueue.add(entry, priority);
    priorityQueue.remove(entry);
  }

  @Fork(value = 2, warmups = 2)
  @Warmup(iterations = 2)
  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  public void addAndRemoveRandomEntries(){
    while(random.nextBoolean()) {
      int priority = ((count++) % 16);
      long entry = Math.abs(random.nextLong());
      priorityQueue.add(entry, priority);
    }
    while(random.nextBoolean() && !priorityQueue.isEmpty()){
      priorityQueue.poll();
    }
  }

  @Fork(value = 2, warmups = 2)
  @Warmup(iterations = 2)
  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  public void addAndRemoveLinearEntries(){
    while(random.nextBoolean()) {
      int priority = ((count++) % 16);
      long entry = Math.abs(lastEntry++);
      priorityQueue.add(entry, priority);
    }
    while(random.nextBoolean() && !priorityQueue.isEmpty()){
      priorityQueue.poll();
    }
  }
}
