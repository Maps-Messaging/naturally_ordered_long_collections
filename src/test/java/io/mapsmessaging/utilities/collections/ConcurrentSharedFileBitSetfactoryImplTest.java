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

import io.mapsmessaging.utilities.collections.bitset.ConcurrentSharedFileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.SharedFileBitSetFactoryImpl;

import java.io.IOException;

public class ConcurrentSharedFileBitSetfactoryImplTest extends BaseSharedFileBitSetFactoryImplTest{
  @Override
  protected SharedFileBitSetFactoryImpl buildFactory() throws IOException {
    return new ConcurrentSharedFileBitSetFactoryImpl(BASE_FILENAME, SHARD_COUNT, WINDOW_SIZE);
  }
}
