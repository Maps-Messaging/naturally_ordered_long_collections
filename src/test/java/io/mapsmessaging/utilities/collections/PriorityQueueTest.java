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

package io.mapsmessaging.utilities.collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Queue;

public abstract class PriorityQueueTest  {

  int[][] insertionTests = {{10,2}, {1000,2}, {1000, 4}, {1000, 10}, {1000, 500}};

  public abstract PriorityQueue<TestData> createQueue(int priorities);

  @Test
  public void testSimpleInsertion(){
    for(int[] values:insertionTests) {
      insertionValidation(values[0], values[1]);
    }
  }

  @Test
  public void testSimpleInsertionAndWalk(){
    for(int[] values:insertionTests) {
      insertionAndWalkValidation(values[0], values[1]);
    }
  }

  @Test
  public void testSimpleInsertionAndDrain(){
    for(int[] values:insertionTests) {
      insertionAndDrainValidation(values[0], values[1]);
    }
  }

  @Test
  public void testLimitedInsertionAndDrain(){
    for(int[] values:insertionTests) {
      limitedInsertionAndDrain(values[0], values[1]);
    }
  }

  @Test
  public void testSimpleInsertionExternal(){
    for(int[] values:insertionTests) {
      insertionValidation(values[0], values[1]);
    }
  }

  @Test
  public void testSimpleInsertionAndWalkExternal(){
    for(int[] values:insertionTests) {
      insertionAndWalkValidation(values[0], values[1]);
    }
  }

  @Test
  public void testSimpleInsertionAndDrainExternal(){
    for(int[] values:insertionTests) {
      insertionAndDrainValidation(values[0], values[1]);
    }
  }

  @Test
  public void testLimitedInsertionAndDrainExternal(){
    for(int[] values:insertionTests) {
      limitedInsertionAndDrain(values[0], values[1]);
    }
  }

  @Test
  public void checkEmptyQueue(){
    PriorityQueue<TestData> priorityQueue = createQueue( 16);
    Assertions.assertNull(priorityQueue.poll());
    Assertions.assertNull(priorityQueue.peek());
  }

  @Test
  public void checkLast(){
    PriorityQueue<TestData> priorityQueue = createQueue( 16);
    for(int x=0;x<100;x++){
      priorityQueue.add(new TestData(x, 8));
    }
    priorityQueue.add(new TestData(101, 0));

    // Normal queue get should return 0, but last should return 101 since it is the lowest

    Assertions.assertEquals(101,priorityQueue.last().uniqueId);
    int x=0;
    while(!priorityQueue.isEmpty()){
      Assertions.assertEquals(x, priorityQueue.last().uniqueId);
      x++;
    }
  }

  @Test
  public void alternativeMethods(){
    for(int[] values:insertionTests) {
      alternateDrainMethods(values[0], values[1]);
    }
  }

  @Test
  public void testRemoveAndGetPriorityValid() {
    int priorities = 4;
    PriorityQueue<TestData> queue = createQueue(priorities);
    TestData entry = new TestData(42, 2);
    queue.offer(entry);

    int returnedPriority = queue.removeAndGetPriority(entry);

    Assertions.assertEquals(2, returnedPriority);
    Assertions.assertEquals(0, queue.size());
  }
  @Test
  public void testRemoveAndGetPriorityInvalid() {
    int priorities = 4;
    PriorityQueue<TestData> queue = createQueue(priorities);
    TestData existing = new TestData(42, 1);
    TestData nonExistent = new TestData(99, 3);

    queue.offer(existing);

    int result = queue.removeAndGetPriority(nonExistent);

    Assertions.assertEquals(-1, result);
    Assertions.assertEquals(1, queue.size());
  }



  private void alternateDrainMethods(int entries, int priorities) {
    PriorityQueue<TestData> priorityQueue = createAndInsert(entries, priorities);
    for (int x = 0; x < priorities; x++) {
      Queue<TestData> list = priorityQueue.priorityStructure.get(x);
      Assertions.assertEquals(entries / priorities, list.size());
      int uniqueIdStart = list.peek().uniqueId;
      for (TestData testData : list) {
        Assertions.assertEquals(uniqueIdStart, testData.uniqueId);
        Assertions.assertEquals(testData.priority, x % priorities);
        uniqueIdStart += priorities;
      }
    }
    Assertions.assertThrows(NoSuchElementException.class, ()-> {
      while(priorityQueue.element() != null){
        priorityQueue.remove();
      }
    });
    Assertions.assertThrows(NoSuchElementException.class, priorityQueue::remove );
  }

  private void limitedInsertionAndDrain(int entries, int priorities){
    PriorityQueue<TestData> priorityQueue = createAndInsert(entries, priorities);
    for(int x=0;x<priorities;x++){
      Queue<TestData> list = priorityQueue.priorityStructure.get(x);
      Assertions.assertEquals(entries/priorities, list.size() );
      int uniqueIdStart = list.peek().uniqueId;
      for (TestData testData : list) {
        Assertions.assertEquals(uniqueIdStart, testData.uniqueId);
        Assertions.assertEquals(testData.priority, x % priorities);
        uniqueIdStart += priorities;
      }
    }

    //
    // Remove a complete priority set
    //
    int removed = priorities/2;
    priorityQueue.priorityStructure.get(removed).clear();
    int size = entries - (entries/priorities);
    Assertions.assertEquals(size, priorityQueue.size());

    //
    // The structure of the queue seems fine, so now lets destructively read it, we should walk backwards from the priority
    // Need to calculate the first priority level we should expect and the unique ID we should expect.
    //

    int priorityStart = priorities -1;
    if(priorityStart == removed){
      priorityStart--;
    }
    int uniqueIdStart = priorityStart;
    int counter = 0;
    int overallCounter =0;
    priorityQueue.recalculateSize();
    while(!priorityQueue.isEmpty()){
      TestData testData = priorityQueue.poll();
      Assertions.assertNotNull(testData);
      Assertions.assertEquals(testData.priority, priorityStart);
      Assertions.assertEquals(testData.uniqueId, uniqueIdStart);
      overallCounter++;
      counter++;
      uniqueIdStart += priorities;
      if(counter == entries/priorities){
        counter = 0;
        priorityStart--;
        if(priorityStart == removed){
          priorityStart--;
        }

        uniqueIdStart = priorityStart;
      }
    }

    Assertions.assertEquals(size, overallCounter);
    Assertions.assertEquals(0, priorityQueue.size());

  }

  private void insertionAndDrainValidation(int entries, int priorities){
    PriorityQueue<TestData> priorityQueue = createAndInsert(entries, priorities);
    for(int x=0;x<priorities;x++){
      Queue<TestData> list = priorityQueue.priorityStructure.get(x);
      Assertions.assertEquals(entries/priorities, list.size() );
      int uniqueIdStart = list.peek().uniqueId;
      for (TestData testData : list) {
        Assertions.assertEquals(uniqueIdStart, testData.uniqueId);
        Assertions.assertEquals(testData.priority, x % priorities);
        uniqueIdStart += priorities;
      }
    }
    //
    // The structure of the queue seems fine, so now lets destructively read it, we should walk backwards from the priority
    // Need to calculate the first priority level we should expect and the unique ID we should expect.
    //

    int priorityStart = priorities -1;
    int uniqueIdStart = priorityStart;
    int counter = 0;
    int overallCounter =0;
    while(!priorityQueue.isEmpty()){
      TestData testData = priorityQueue.poll();
      Assertions.assertNotNull(testData);
      Assertions.assertEquals(testData.priority, priorityStart);
      Assertions.assertEquals(testData.uniqueId, uniqueIdStart);
      overallCounter++;
      counter++;
      uniqueIdStart += priorities;
      if(counter == entries/priorities){
        counter = 0;
        priorityStart--;
        uniqueIdStart = priorityStart;
      }
    }

    Assertions.assertEquals(entries, overallCounter);
    Assertions.assertEquals(0, priorityQueue.size());

  }

  private void insertionAndWalkValidation(int entries, int priorities){
    PriorityQueue<TestData> priorityQueue = createAndInsert(entries, priorities);
    for(int x=0;x<priorities;x++){
      Queue<TestData> list = priorityQueue.priorityStructure.get(x);
      Assertions.assertEquals(entries/priorities, list.size() );
      int uniqueIdStart = list.peek().uniqueId;
      for (TestData testData : list) {
        Assertions.assertEquals(uniqueIdStart, testData.uniqueId);
        Assertions.assertEquals(testData.priority, x % priorities);
        uniqueIdStart += priorities;
      }
    }
  }

  private void insertionValidation(int entries, int priorities){
    PriorityQueue<TestData> priorityQueue = createAndInsert(entries, priorities);
    for(int x=0;x<priorities;x++){
      Queue<TestData> list = priorityQueue.priorityStructure.get(x);
      Assertions.assertEquals(entries/priorities, list.size() );
    }
  }

  private PriorityQueue<TestData> createAndInsert(int entries, int priorities){
    PriorityQueue<TestData> priorityQueue = createQueue(priorities);
    Assertions.assertEquals(priorityQueue.priorityStructure.size(), priorities);
    for(int x=0;x<entries;x++){
      priorityQueue.offer(new TestData(x,x%priorities));
    }
    Assertions.assertEquals(priorityQueue.size(), entries);

    return priorityQueue;
  }

  static class TestDataPriorityFactory implements PriorityFactory<TestData>{

    @Override
    public int getPriority(TestData value) {
      return value.priority;
    }
  }

  static class TestData implements PriorityEntry{

    private final int uniqueId;
    private final int priority;

    public TestData(int uniqueId, int priority){
      this.uniqueId = uniqueId;
      this.priority = priority;
    }

    @Override
    public int getPriority() {
      return priority;
    }
  }
}
