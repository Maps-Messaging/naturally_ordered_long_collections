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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FileBitSetFactoryImpl extends BitSetFactory {

  private static final int BITS_PER_BYTE = 8;
  private static final int HEADER_SIZE = 16;

  private final List<FileOffsetBitSet> free;
  private final List<FileOffsetBitSet> used;

  private final String filename;
  private final int bufferSize;
  private final byte[] emptyBuffer;

  private RandomAccessFile raf;
  private boolean closed;
  private boolean deleted;
  private ScheduledFuture<?> deleteTask;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  public FileBitSetFactoryImpl(@NonNull @NotNull String filename, int size) throws IOException {
    super(size);
    closed = false;
    deleted = false;
    this.filename = filename;
    bufferSize = size / BITS_PER_BYTE;
    free = new ArrayList<>();
    used = new ArrayList<>();
    var testFile = new File(filename);
    var parent = testFile.getParentFile();
    if (parent != null && !parent.exists()) {
      Files.createDirectories(testFile.getParentFile().toPath());
    }
    emptyBuffer = new byte[bufferSize];
    for (var x = 0; x < bufferSize; x++) {
      emptyBuffer[x] = 0;
    }

    if(testFile.exists() && testFile.length() > 0) {
      raf = new RandomAccessFile(testFile, "rw");
      loadFile();
      if(used.isEmpty()) {
        deleteFiles();
      }
    }
    else{
      deleted = true; // Mark it as being active but file has been deleted, allowing first operation to create it
    }
  }

  private void loadFile() throws IOException {
    long len = raf.length();
    long pos = 0;
    while (pos < len) {
      raf.seek(pos);
      long uniqueId = raf.readLong();
      long offset = raf.readLong();
      pos += HEADER_SIZE;
      ByteBufferBackedBitMap bitmap = map(pos, uniqueId);
      FileOffsetBitSet bitset = new FileOffsetBitSet(bitmap, pos - HEADER_SIZE, offset, this);
      if (uniqueId != -1) {
        used.add(bitset);
      } else {
        free.add(bitset);
      }
      pos += bufferSize;
    }
  }

  @Override
  public void delete() throws IOException {
    close();
    deleteFiles();
  }

  @Override
  public void close() throws IOException {
    if(closed)return;
    closed = true;
    boolean delete = used.isEmpty();
    clearList(used);
    clearList(free);
    if(raf != null && raf.getChannel().isOpen()) {
      raf.close();
    }
    if(delete){
      deleteFiles();
    }
    scheduler.shutdownNow();
  }

  private void deleteFiles() throws IOException {
    if(raf != null && raf.getChannel().isOpen() ){
      clearList(used);
      clearList(free);
      raf.close();
    }
    File file = new File(filename);
    Files.deleteIfExists(file.toPath());
    used.clear();
    free.clear();
    deleted = true;
  }

  public String getFileName() {
    return filename;
  }

  @Override
  public OffsetBitSet open(long uniqueId, long offset) throws IOException {
    checkState();
    if(closed)throw new IllegalStateException ("BitSet file is closed");
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

  @Override
  public void close(@NonNull @NotNull OffsetBitSet bitset) {
    // No Op, since it is still used and can not be released on its own
  }

  @Override
  public void release(@NonNull @NotNull OffsetBitSet bitset) {
    checkState();
    bitset.reset(0, -1);
    try {
      updateRecord(((FileOffsetBitSet) bitset).getPosition(), -1, 0);
    } catch (IOException e) {
      // Log this
    }
    free.add((FileOffsetBitSet) bitset);
    used.remove((FileOffsetBitSet) bitset);
    if(used.isEmpty()){
      scheduleDelete();
    }
  }


  @Override
  public List<OffsetBitSet> get(long uniqueId) {
    if(deleted){
      return new ArrayList<>(); // if the file is deleted, there are no bitsets, lets not open it
    }
    checkState();
    if(uniqueId == -1){
      return getList(free, uniqueId);
    }
    return getList(used, uniqueId);
  }

  @Override
  public List<Long> getUniqueIds() {
    if(deleted){
      return new ArrayList<>(); // if the file is deleted, there are no unique ids, lets not open it
    }
    checkState();
    Set<Long> ids = new LinkedHashSet<>();
    for (FileOffsetBitSet bitset : used) {
      ids.add(bitset.getUniqueId());
    }
    return new ArrayList<>(ids);
  }

  private ByteBufferBackedBitMap map(long pos, long uniqueId) throws IOException {
    return new ByteBufferBackedBitMap(raf.getChannel().map(MapMode.READ_WRITE, pos, bufferSize), 0, uniqueId);
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


  private void clearList(@NonNull @NotNull List<FileOffsetBitSet> list) {
    for (FileOffsetBitSet bitmap : list) {
      ByteBufferBackedBitMap mapped = (ByteBufferBackedBitMap) bitmap.getBitSet();
      bitmap.releaseBitSet();
      unmap(mapped);
    }
    list.clear();
  }

  private void unmap(@NonNull @NotNull ByteBufferBackedBitMap mapped) {
    ByteBuffer backing = mapped.clearBacking();
    MappedBufferHelper.closeDirectBuffer(backing);
  }

  private void scheduleDelete() {
    if (deleteTask != null && !deleteTask.isDone()) return;
    deleteTask = scheduler.schedule(() -> {
      try {
        deleteFiles();
      } catch (IOException ignored) {
        // Ignore this
      }
    }, 10, TimeUnit.SECONDS);
  }

  private void checkState() {
    if(closed) throw new IllegalStateException("BitSet file is closed");
    if (deleteTask != null && !deleteTask.isDone()) deleteTask.cancel(false);
    if(deleted) {
      reopen();
    }
  }

  private void reopen() {
    try {
      deleted = false;
      this.raf = new RandomAccessFile(new File(filename), "rw");
      loadFile();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to reopen file", e);
    }
  }
}
