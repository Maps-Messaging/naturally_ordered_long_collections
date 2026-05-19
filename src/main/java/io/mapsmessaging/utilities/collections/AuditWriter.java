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

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Writes one JSON object per line (JSONL). Synchronized to keep lines intact under concurrency.
 */
public class AuditWriter implements Closeable {

  private final BufferedWriter writer;

  public AuditWriter(Path auditFile) {
    try {
      this.writer = Files.newBufferedWriter(
          auditFile,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND,
          StandardOpenOption.WRITE
      );
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  public synchronized void writeEvent(String method, String args, String result, Throwable error) {
    String timestamp = Instant.now().toString();
    String threadName = Thread.currentThread().getName();

    String line = buildJsonLine(timestamp, threadName, method, args, result, error);

    try {
      writer.write(line);
      writer.newLine();
      writer.flush();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private String buildJsonLine(String timestamp, String threadName, String method, String args, String result, Throwable error) {
    StringBuilder builder = new StringBuilder(256);
    builder.append('{');
    appendJsonField(builder, "ts", timestamp);
    builder.append(',');
    appendJsonField(builder, "thread", threadName);
    builder.append(',');
    appendJsonField(builder, "method", method);
    builder.append(',');
    appendJsonField(builder, "args", args);

    if (result != null) {
      builder.append(',');
      appendJsonField(builder, "result", result);
    }

    if (error != null) {
      builder.append(',');
      appendJsonField(builder, "errorType", error.getClass().getName());
      builder.append(',');
      appendJsonField(builder, "errorMessage", String.valueOf(error.getMessage()));
    }

    builder.append('}');
    return builder.toString();
  }

  private void appendJsonField(StringBuilder builder, String key, String value) {
    builder.append('"');
    builder.append(escapeJson(key));
    builder.append('"');
    builder.append(':');
    builder.append('"');
    builder.append(escapeJson(value));
    builder.append('"');
  }

  private String escapeJson(String value) {
    StringBuilder builder = new StringBuilder(value.length() + 16);
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      if (ch == '"') {
        builder.append("\\\"");
      } else if (ch == '\\') {
        builder.append("\\\\");
      } else if (ch == '\n') {
        builder.append("\\n");
      } else if (ch == '\r') {
        builder.append("\\r");
      } else if (ch == '\t') {
        builder.append("\\t");
      } else if (ch < 0x20) {
        builder.append(String.format("\\u%04x", (int) ch));
      } else {
        builder.append(ch);
      }
    }
    return builder.toString();
  }

  @Override
  public synchronized void close() {
    try {
      writer.close();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}

