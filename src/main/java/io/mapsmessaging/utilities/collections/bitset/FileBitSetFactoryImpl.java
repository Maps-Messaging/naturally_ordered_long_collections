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

import io.mapsmessaging.utilities.collections.MappedBufferHelper;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FileBitSetFactoryImpl extends BitSetFactory {

  private static final int BITS_PER_BYTE = 8;
  private static final int HEADER_SIZE = 16;

  private final List<FileOffsetBitSet> free;
  private final List<FileOffsetBitSet> used;

  private final String filename;
  private final RandomAccessFile raf;
  private final int bufferSize;
  private final byte[] emptyBuffer;

  public FileBitSetFactoryImpl(@NonNull @NotNull String filename, int size) throws IOException {
    super(size);
    this.filename = filename;
    bufferSize = size / BITS_PER_BYTE;
    free = new ArrayList<>();
    used = new ArrayList<>();
    var testFile = new File(filename);
    var parent = testFile.getParentFile();
    if (parent != null && !parent.exists()) {
      Files.createDirectories(testFile.getParentFile().toPath());
    }

    raf = new RandomAccessFile(testFile, "rw");
    emptyBuffer = new byte[bufferSize];
    for (var x = 0; x < bufferSize; x++) {
      emptyBuffer[x] = 0;
    }
    long len = raf.length();
    long pos = 0;
    while (pos < len) {
      raf.seek(pos);
      var uniqueId = raf.readLong();
      var offset = raf.readLong();
      pos += HEADER_SIZE;
      ByteBufferBackedBitMap bitmap = map(pos, uniqueId);
      FileOffsetBitSet bitset = new FileOffsetBitSet(bitmap, pos - HEADER_SIZE, offset, this);
      if(uniqueId != -1){
        used.add(bitset);
      }
      else {
        free.add(bitset);
      }
      pos += bufferSize;
    }
  }

  @Override
  public void delete() throws IOException {
    close();
    var check = new File(filename);
    if (check.exists()) {
      Files.delete(check.toPath());
    }
  }

  @Override
  public void close() throws IOException {
    clearList(used);
    clearList(free);
    raf.close();
  }

  private void clearList(@NonNull @NotNull List<FileOffsetBitSet> list) {
    for (FileOffsetBitSet bitmap : list) {
      ByteBufferBackedBitMap mapped = (ByteBufferBackedBitMap) bitmap.getBitSet();
      bitmap.releaseBitSet();
      unmap(mapped);
    }
    list.clear();
  }

  public String getFileName() {
    return filename;
  }

  @Override
  public OffsetBitSet open(long uniqueId, long offset) throws IOException {
    FileOffsetBitSet response;
    offset = getStartIndex(offset);
    if (free.isEmpty()) {
      long end = raf.length();
      createRecord(end, uniqueId, offset);
      ByteBufferBackedBitMap bitmap = map(end + HEADER_SIZE, uniqueId);
      response = new FileOffsetBitSet(bitmap, end, offset, this);
    } else {
      response = free.remove(0);
      updateRecord(response.getPosition(), uniqueId, offset);
      response.reset(offset, uniqueId);
    }
    used.add(response);
    return response;
  }
  
  private void createRecord(long position, long uniqueId, long offset) throws IOException {
    raf.seek(position);
    raf.writeLong(uniqueId);
    raf.writeLong(offset);
    raf.write(emptyBuffer);
  }
  
  private void updateRecord(long position, long uniqueId, long offset) throws IOException {
    raf.seek(position);
    raf.writeLong(uniqueId);
    raf.writeLong(offset);
  }

  @Override
  public void close(@NonNull @NotNull OffsetBitSet bitset) {
    FileOffsetBitSet fb = (FileOffsetBitSet) bitset;
    used.remove(fb);
    fb.reset(0, -1);
    try {
      updateRecord(fb.getPosition(), fb.getUniqueId(), fb.getStart());
    } catch (IOException e) {

    }
    free.add(fb);
  }

  @Override
  public void release(@NonNull @NotNull OffsetBitSet bitset) {
    bitset.reset(0, -1);
    try {
      updateRecord(((FileOffsetBitSet) bitset).getPosition(), -1, 0);
    } catch (IOException e) {
      // Log this
    }
    free.add((FileOffsetBitSet) bitset);
    used.remove((FileOffsetBitSet) bitset);
  }


  @Override
  public List<OffsetBitSet> get(long uniqueId) {
    if(uniqueId == -1){
      return getList(free, uniqueId);
    }
    return getList(used, uniqueId);
  }

  private List<OffsetBitSet> getList(List<FileOffsetBitSet> list, long uniqueId) {
    List<OffsetBitSet> response = new ArrayList<>();
    for (FileOffsetBitSet bitset : list) {
      if (bitset.getUniqueId() == uniqueId) {
        response.add(bitset);
      }
    }
    return response;
  }

  @Override
  public List<Long> getUniqueIds() {
    Set<Long> ids = new LinkedHashSet<>();
    for (FileOffsetBitSet bitset : used) {
      ids.add(bitset.getUniqueId());
    }
    return new ArrayList<>(ids);
  }

  protected ByteBufferBackedBitMap map(long pos, long uniqueId) throws IOException {
    return new ByteBufferBackedBitMap(raf.getChannel().map(MapMode.READ_WRITE, pos, bufferSize), 0, uniqueId);
  }

  private void unmap(@NonNull @NotNull ByteBufferBackedBitMap mapped) {
    ByteBuffer backing = mapped.clearBacking();
    MappedBufferHelper.closeDirectBuffer(backing);
  }
}
