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

import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.OffsetBitSet;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForcedReleaseDeterministicTest {

  static final class ReleasingFactory extends BitSetFactoryImpl {
    ReleasingFactory(int size) {
      super(size);
    }

    @Override
    public void release(OffsetBitSet bitset) {
      bitset.releaseBitSet(); // force the exact failure mode
    }
  }

  @Test
  void releasingBitsetThenTouchingIt_throwsIllegalState() {
    NaturalOrderedLongQueue queue = new NaturalOrderedLongQueue(1L, new ReleasingFactory(8192));

    // Ensure at least one OffsetBitSet exists
    queue.offer(123L);

    // Grab the underlying first bitset (same package access: tree is protected)
    Map.Entry<Long, OffsetBitSet> firstEntry = queue.tree.firstEntry();
    assertNotNull(firstEntry);

    OffsetBitSet bitset = firstEntry.getValue();
    assertTrue(bitset.isActive());

    // Create an iterator (simulates "reference escapes")
    Iterator<Long> iterator = bitset.iterator();
    assertNotNull(iterator);

    // Force release
    queue.factory.release(bitset);

    // Now touching it MUST throw
    IllegalStateException ex = assertThrows(IllegalStateException.class, bitset::isEmpty);
    assertTrue(ex.getMessage().contains("released"));
  }
}
