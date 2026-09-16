/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
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
import lombok.Getter;
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

  private static final long FILE_MAGIC = 0x4E4F4C4342495432L;
  private static final int FILE_VERSION = 2;
  private static final int FILE_HEADER_SIZE = Long.BYTES + Integer.BYTES + Integer.BYTES;
  private static final int LEGACY_RECORD_HEADER_SIZE = Long.BYTES * 2;
  private static final int RECORD_HEADER_SIZE = Long.BYTES * 3;
  private static final long STATE_FREE = 0L;
  private static final long STATE_ALLOCATED = 1L;

  private final List<FileOffsetBitSet> free;
  private final List<FileOffsetBitSet> used;

  private final String filename;
  private final int bufferSize;
  private final byte[] emptyBuffer;
  @Getter
  private final int shard;
  private RandomAccessFile raf;
  private boolean closed;
  private boolean deleted;

  public FileBitSetFactoryImpl(@NonNull @NotNull String filename, int size) throws IOException {
    this(filename, size, 0);
  }

  public FileBitSetFactoryImpl(@NonNull @NotNull String filename, int size, int shard) throws IOException {
    super(size);
    this.shard = shard;
    closed = false;
    deleted = false;
    this.filename = filename;
    bufferSize = ((size + 63) / 64) * Long.BYTES;
    free = new ArrayList<>();
    used = new ArrayList<>();
    File testFile = prepareBackingFile(filename);
    emptyBuffer = new byte[bufferSize];

    if (testFile.exists() && testFile.length() > 0) {
      raf = new RandomAccessFile(testFile, "rw");
      loadFile();
      if (used.isEmpty()) {
        deleteFiles();
      }
    } else {
      deleted = true;
    }
  }

  @Override
  public String toString() {
    return String.format("FileName:%s%n Size:%d%n Free:%d%n Used:%d%n Closed:%s%n Deleted:%s%n Shard:%d%n",
        filename, bufferSize, free.size(), used.size(), closed, deleted, shard);
  }

  private void loadFile() throws IOException {
    if (raf.length() == 0) {
      writeFileHeader();
      return;
    }

    raf.seek(0);
    long marker = raf.readLong();
    if (marker == FILE_MAGIC) {
      int version = raf.readInt();
      int persistedWindowSize = raf.readInt();
      if (version != FILE_VERSION) {
        throw new IOException("Unsupported bitset file version " + version);
      }
      if (persistedWindowSize != windowSize) {
        throw new IOException(
            "Bitset file window size " + persistedWindowSize + " does not match configured size " + windowSize);
      }
      loadVersion2();
    } else {
      migrateLegacyFile();
    }
  }

  private void loadVersion2() throws IOException {
    long len = raf.length();
    long recordSize = RECORD_HEADER_SIZE + (long) bufferSize;
    long dataLength = len - FILE_HEADER_SIZE;
    if (dataLength < 0 || dataLength % recordSize != 0) {
      throw new IOException("Invalid version 2 bitset file length " + len);
    }

    long pos = FILE_HEADER_SIZE;
    while (pos < len) {
      raf.seek(pos);
      long state = raf.readLong();
      long uniqueId = raf.readLong();
      long offset = raf.readLong();
      long bitmapPosition = pos + RECORD_HEADER_SIZE;
      ByteBufferBackedBitMap bitmap = map(bitmapPosition, uniqueId);
      boolean allocated = state == STATE_ALLOCATED;
      if (!allocated && state != STATE_FREE) {
        throw new IOException("Invalid bitset record state " + state + " at position " + pos);
      }
      FileOffsetBitSet bitset = new FileOffsetBitSet(
          bitmap, pos, offset, getWindowLength(offset), this, shard, allocated);
      if (allocated) {
        used.add(bitset);
      } else {
        free.add(bitset);
      }
      pos += recordSize;
    }
  }

  private void migrateLegacyFile() throws IOException {
    int legacyBufferSize = windowSize / Byte.SIZE;
    long legacyRecordSize = LEGACY_RECORD_HEADER_SIZE + (long) legacyBufferSize;
    long length = raf.length();
    if (legacyBufferSize <= 0 || length % legacyRecordSize != 0) {
      throw new IOException("Unrecognised legacy bitset file format");
    }

    List<LegacyRecord> records = new ArrayList<>();
    long pos = 0;
    while (pos < length) {
      raf.seek(pos);
      long uniqueId = raf.readLong();
      long offset = raf.readLong();
      byte[] payload = new byte[legacyBufferSize];
      raf.readFully(payload);
      if (uniqueId != -1L) {
        records.add(new LegacyRecord(uniqueId, offset, payload));
      }
      pos += legacyRecordSize;
    }

    raf.setLength(0);
    writeFileHeader();
    for (LegacyRecord record : records) {
      long recordPosition = raf.length();
      raf.seek(recordPosition);
      raf.writeLong(STATE_ALLOCATED);
      raf.writeLong(record.uniqueId());
      raf.writeLong(record.offset());
      raf.write(record.payload());
      int padding = bufferSize - record.payload().length;
      if (padding > 0) {
        raf.write(new byte[padding]);
      }
    }
    used.clear();
    free.clear();
    loadVersion2();
  }

  private void writeFileHeader() throws IOException {
    raf.seek(0);
    raf.writeLong(FILE_MAGIC);
    raf.writeInt(FILE_VERSION);
    raf.writeInt(windowSize);
  }

  @Override
  public void delete() throws IOException {
    close();
    deleteFiles();
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    boolean delete = used.isEmpty();
    clearList(used);
    clearList(free);
    if (raf != null && raf.getChannel().isOpen()) {
      raf.close();
    }
    if (delete) {
      deleteFiles();
    }
  }

  public synchronized void cleanupIfPossible(long minSize) throws IOException {
    if (used.isEmpty() && raf != null && raf.length() > minSize) {
      deleteFiles();
    }
  }

  private synchronized void deleteFiles() throws IOException {
    if (!used.isEmpty()) {
      return;
    }
    if (raf != null && raf.getChannel().isOpen()) {
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
  public synchronized OffsetBitSet open(long uniqueId, long offset) throws IOException {
    checkState();
    if (closed) throw new IllegalStateException("BitSet file is closed");

    long start = getStartIndex(offset);
    int logicalLength = getWindowLength(start);
    FileOffsetBitSet response;
    if (free.isEmpty()) {
      long position = raf.length();
      createRecord(position, uniqueId, start);
      ByteBufferBackedBitMap bitmap = map(position + RECORD_HEADER_SIZE, uniqueId);
      response = new FileOffsetBitSet(bitmap, position, start, logicalLength, this, shard, true);
    } else {
      response = free.remove(0);
      updateRecord(response.getPosition(), STATE_ALLOCATED, uniqueId, start);
      response.reset(start, uniqueId, logicalLength);
      response.setAllocated(true);
    }
    used.add(response);
    return response;
  }

  @Override
  public void close(@NonNull @NotNull OffsetBitSet bitset) {
    // No Op, since it is still used and can not be released on its own
  }

  @Override
  public synchronized void release(@NonNull @NotNull OffsetBitSet bitset) {
    checkState();
    FileOffsetBitSet fileBitSet = (FileOffsetBitSet) bitset;
    if (!used.remove(fileBitSet)) {
      return;
    }
    try {
      updateRecord(fileBitSet.getPosition(), STATE_FREE, fileBitSet.getUniqueId(), fileBitSet.getStart());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to mark bitset record free", e);
    }
    fileBitSet.clearAll();
    fileBitSet.setAllocated(false);
    free.add(fileBitSet);
  }

  @Override
  public List<OffsetBitSet> get(long uniqueId) {
    if (deleted) {
      return new ArrayList<>();
    }
    checkState();
    return getList(used, uniqueId);
  }

  @Override
  public List<OffsetBitSet> getFreeBitSets() {
    if (deleted) {
      return new ArrayList<>();
    }
    checkState();
    return new ArrayList<>(free);
  }

  @Override
  public List<Long> getUniqueIds() {
    if (deleted) {
      return new ArrayList<>();
    }
    checkState();
    Set<Long> ids = new LinkedHashSet<>();
    for (FileOffsetBitSet bitset : used) {
      ids.add(bitset.getUniqueId());
    }
    return new ArrayList<>(ids);
  }

  protected File prepareBackingFile(@NonNull String filename) throws IOException {
    File file = new File(filename);
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      Files.createDirectories(parent.toPath());
    }
    return file;
  }

  private ByteBufferBackedBitMap map(long pos, long uniqueId) throws IOException {
    return new ByteBufferBackedBitMap(raf.getChannel().map(MapMode.READ_WRITE, pos, bufferSize), 0, uniqueId);
  }

  private synchronized List<OffsetBitSet> getList(List<FileOffsetBitSet> list, long uniqueId) {
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
    raf.writeLong(STATE_ALLOCATED);
    raf.writeLong(uniqueId);
    raf.writeLong(offset);
    raf.write(emptyBuffer);
  }

  private void updateRecord(long position, long state, long uniqueId, long offset) throws IOException {
    raf.seek(position);
    raf.writeLong(state);
    raf.writeLong(uniqueId);
    raf.writeLong(offset);
  }

  private void clearList(@NonNull @NotNull List<FileOffsetBitSet> list) {
    for (FileOffsetBitSet bitmap : list) {
      if (bitmap.isActive()) {
        ByteBufferBackedBitMap mapped = (ByteBufferBackedBitMap) bitmap.getBitSet();
        bitmap.releaseBitSet();
        unmap(mapped);
      }
    }
    list.clear();
  }

  private void unmap(@NonNull @NotNull ByteBufferBackedBitMap mapped) {
    ByteBuffer backing = mapped.clearBacking();
    MappedBufferHelper.closeDirectBuffer(backing);
  }

  private synchronized void checkState() {
    if (closed) throw new IllegalStateException("BitSet file is closed");
    if (deleted) {
      reopen();
    }
  }

  private void reopen() {
    try {
      deleted = false;
      File file = prepareBackingFile(filename);
      raf = new RandomAccessFile(file, "rw");
      loadFile();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to reopen file", e);
    }
  }

  private record LegacyRecord(long uniqueId, long offset, byte[] payload) {
  }
}
