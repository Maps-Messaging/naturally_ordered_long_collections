/*
 * Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 * Licensed under the Apache License, Version 2.0 with the Commons Clause
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package io.mapsmessaging.utilities.collections;

import io.mapsmessaging.utilities.collections.bitset.BitSetFactory;
import io.mapsmessaging.utilities.collections.bitset.BitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ByteBufferBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.ConcurrentSharedFileBitSetFactoryImpl;
import io.mapsmessaging.utilities.collections.bitset.FileBitSetFactoryImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Server-style event lifecycle benchmarks. Each bulk invocation performs 1,000,001
 * additions and 1,000,001 deletions. Logical operation counts are specified so
 * throughput reports comparable events/second across backends.
 *
 * These are benchmarks, not JUnit tests; run explicitly through org.openjdk.jmh.Main.
 */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 5)
public class MSG423QueueWorkloadJMH {

  private static final int EVENT_COUNT = 1_000_001;
  private static final int SHARDS = 4;

  @Param({"heap", "direct", "mapped", "shared"})
  public String backend;

  @Param({"4096"})
  public int windowSize;

  private Path directory;
  private BitSetFactory factory;
  private NaturalOrderedLongQueue queue;
  private long nextMonotonicId;

  @Setup(Level.Trial)
  public void setup() throws IOException {
    directory = Files.createTempDirectory("msg423-jmh-");
    factory = switch (backend) {
      case "heap" -> new BitSetFactoryImpl(windowSize);
      case "direct" -> new ByteBufferBitSetFactoryImpl(windowSize);
      case "mapped" -> new FileBitSetFactoryImpl(directory.resolve("mapped.bin").toString(), windowSize);
      case "shared" -> new ConcurrentSharedFileBitSetFactoryImpl(directory.resolve("shared.bin").toString(), SHARDS, windowSize);
      default -> throw new IllegalArgumentException("Unsupported backend " + backend);
    };
    queue = new NaturalOrderedLongQueue(-1L, factory);
    nextMonotonicId = windowSize - 1L;
  }

  @TearDown(Level.Trial)
  public void teardown() throws IOException {
    if (queue != null) {
      queue.clear();
      queue.close();
    }
    if (factory != null) {
      factory.close();
    }
    if (directory != null) {
      Files.deleteIfExists(directory.resolve("mapped.bin"));
      for (int shard = 0; shard < SHARDS; shard++) {
        Files.deleteIfExists(directory.resolve("shared.bin_" + shard));
      }
      Files.deleteIfExists(directory);
    }
  }

  @Benchmark
  @OperationsPerInvocation(4)
  public void addRemoveZeroAndOne(Blackhole blackhole) {
    boolean changed = queue.offer(0L);
    changed &= queue.remove(0L);
    changed &= queue.offer(1L);
    changed &= queue.remove(1L);
    blackhole.consume(changed);
  }

  @Benchmark
  @OperationsPerInvocation(2)
  public void monotonicSingleEventAddRemove(Blackhole blackhole) {
    long id = nextMonotonicId++;
    boolean changed = queue.offer(id);
    changed &= queue.remove(id);
    blackhole.consume(changed);
  }

  @Benchmark
  @OperationsPerInvocation(EVENT_COUNT * 2)
  public void addMillionThenRemoveMillion(Blackhole blackhole) {
    boolean changed = true;
    for (long id = 0; id < EVENT_COUNT; id++) {
      changed &= queue.offer(id);
    }
    for (long id = 0; id < EVENT_COUNT; id++) {
      changed &= queue.remove(id);
    }
    blackhole.consume(changed);
  }

  @Benchmark
  @OperationsPerInvocation(EVENT_COUNT * 2)
  public void addMillionThenPollMillion(Blackhole blackhole) {
    for (long id = 0; id < EVENT_COUNT; id++) {
      queue.offer(id);
    }
    boolean ordered = true;
    for (long id = 0; id < EVENT_COUNT; id++) {
      Long actual = queue.poll();
      ordered &= actual != null && actual.longValue() == id;
    }
    blackhole.consume(ordered);
  }
}
