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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

class FileBitSetFactoryTest extends BitSetFactoryTest{



  @Override
  BitSetFactory createFactory(int size) throws IOException {
    factory = new FileBitSetFactoryImpl("./bitsetTest.bit", size);
    return factory;
  }

  @Override
  @Test
  void checkListReturns() throws IOException {
    try( BitSetFactory bitSetFactory = createFactory(1024)) {
      long id = System.currentTimeMillis();
      OffsetBitSet bitSet = bitSetFactory.open(id, 1);
      bitSetFactory.release(bitSet);
      Assertions.assertTrue(bitSetFactory.getUniqueIds().isEmpty());
      Assertions.assertFalse(bitSetFactory.get(-1).isEmpty());
      Assertions.assertEquals(1, bitSetFactory.get(-1).size());
    }
  }

  @Test
  void checkReload() throws IOException {
    try( BitSetFactory bitSetFactory = createFactory(1024)) {
      long id = System.currentTimeMillis();
      OffsetBitSet bitSet = bitSetFactory.open(id, 1);
      for (int x = 0; x < 1024; x++) {
        bitSet.set(x);
      }
      factory.close();
    }

    try( BitSetFactory bitSetFactory = createFactory(1024)) {
      Assertions.assertFalse(bitSetFactory.getUniqueIds().isEmpty());
      for(long id:bitSetFactory.getUniqueIds()){
        List<OffsetBitSet> bitSets = bitSetFactory.get(id);
        Assertions.assertFalse(bitSets.isEmpty());
        for(OffsetBitSet bitSet:bitSets){
          for(int x=0;x<1024;x++){
            Assertions.assertTrue(bitSet.isSet(x));
            bitSet.clear(x);
            Assertions.assertFalse(bitSet.isSet(x));
          }
          bitSetFactory.release(bitSet);
        }
      }
    }
    try( BitSetFactory bitSetFactory = createFactory(1024)) {
      Assertions.assertTrue(bitSetFactory.getUniqueIds().isEmpty());
    }
  }

  @Test
  void testAutoDeleteAndReopen() throws Exception {
    String path = "./bitsetTestDelete.bit";
    File file = new File(path);
    if (file.exists()) file.delete();

    FileBitSetFactoryImpl factory = new FileBitSetFactoryImpl(path, 1024);
    long id = System.currentTimeMillis();
    OffsetBitSet bitSet = factory.open(id, 0);
    factory.release(bitSet);

    // Wait for auto-delete (10 sec) + buffer
    Thread.sleep(12_000);

    Assertions.assertFalse(file.exists(), "File should have been deleted");

    // Now access it to cause reopen
    OffsetBitSet reopened = factory.open(id, 0);
    Assertions.assertNotNull(reopened);
    reopened.set(0);
    Assertions.assertTrue(reopened.isSet(0));
    factory.release(reopened);
    factory.close();
  }
}
