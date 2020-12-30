/*
 * Copyright (c) 2013-2018, Bingo.Chen (finesoft@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.corant.shared.util;

import static org.corant.shared.util.Assertions.shouldBeTrue;
import static org.corant.shared.util.Assertions.shouldNotNull;
import static org.corant.shared.util.Lists.listOf;
import static org.corant.shared.util.Streams.streamOf;
import static org.corant.shared.util.Strings.EMPTY;
import static org.corant.shared.util.Strings.NEWLINE;
import static org.corant.shared.util.Strings.RETURN;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.corant.shared.exception.CorantRuntimeException;

/**
 * corant-shared
 *
 * @author bingo 上午10:21:10
 *
 */
public class Texts {

  protected static final char CSV_FIELD_DELIMITER = ',';
  protected static final char CSV_FIELD_QUOTES = '"';
  protected static final String CSV_FIELD_DELIMITER_STRING = ",";
  protected static final String CSV_FIELD_QUOTES_STRING = "\"";
  protected static final String CSV_DOUBLE_QUOTES = "\"\"";

  private Texts() {}

  /**
   * CSV rows from file input stream, use for read text file line by line.
   *
   * @param file the CSV file
   * @param offset the offset start from 0
   * @param limit the number of rows returned streamCSVRows
   */
  public static Stream<List<String>> asCSVLines(final File file, int offset, int limit) {
    FileInputStream fis;
    try {
      fis = new FileInputStream(shouldNotNull(file));
    } catch (FileNotFoundException e1) {
      throw new CorantRuntimeException(e1);
    }
    return asCSVLines(fis, offset, (i, t) -> limit >= 1 && i > limit).onClose(() -> {
      try {
        fis.close();
      } catch (IOException e) {
        throw new CorantRuntimeException(e);
      }
    });
  }

  /**
   * CSV rows from input stream, use for read CSV file line by line.
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is the CSV format input stream
   * @return streamCSVRows
   */
  public static Stream<List<String>> asCSVLines(final InputStream is) {
    return asCSVLines(is, 0, null);
  }

  /**
   * CSV rows from input stream, use for read CSV file line by line.
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is the CSV format input stream
   * @param offset the offset start from 0
   * @param terminator use to brake out the stream, terminator return true means need to brake out
   */
  public static Stream<List<String>> asCSVLines(final InputStream is, int offset,
      BiPredicate<Integer, String> terminator) {
    final BufferedReader reader = new CSVBufferedReader(new InputStreamReader(is));
    return lines(reader, offset, terminator, Texts::fromCSVLine);
  }

  /**
   * Convert string to byte array input stream.
   *
   * @param data
   * @return asInputStream
   */
  public static InputStream asInputStream(String data) {
    return new ByteArrayInputStream(shouldNotNull(data).getBytes());
  }

  /**
   * Convert string to byte array input stream with charset
   *
   * @param data
   * @param charset
   * @return asInputStream
   */
  public static InputStream asInputStream(String data, Charset charset) {
    return new ByteArrayInputStream(shouldNotNull(data).getBytes(charset));
  }

  /**
   * Parse CSV line to list
   *
   * NOTE: Some code come from com.sun.tools.jdeprscan.CSV, if there is infringement, please inform
   * me(finesoft@gmail.com).
   *
   * @param line the CSV format line
   * @return the CSV fields
   */
  public static List<String> fromCSVLine(String line) {
    List<String> result = new ArrayList<>();
    if (line != null) {
      StringBuilder buf = new StringBuilder();
      byte state = 0; // 0:start,1:in field,2:in field quote,3:end field quote
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        switch (c) {
          case CSV_FIELD_DELIMITER:
            switch (state) {
              case 2:
                buf.append(CSV_FIELD_DELIMITER);
                break;
              default:
                result.add(buf.toString());
                buf.setLength(0);
                state = 0;
                break;
            }
            break;
          case CSV_FIELD_QUOTES:
            switch (state) {
              case 0:
                state = 2;
                break;
              case 2:
                state = 3;
                break;
              case 1:
                throw new IllegalArgumentException(
                    String.format("Unexpected csv quote, line: [%s] char at: [%d]", line, i));
              case 3:
                buf.append(CSV_FIELD_QUOTES);
                state = 2;
                break;
            }
            break;
          default:
            switch (state) {
              case 0:
                state = 1;
                break;
              case 1:
              case 2:
                break;
              case 3:
                throw new IllegalArgumentException(String.format(
                    "Extra csv character after quoted string, line: [%s] char at: [%d]", line, i));
            }
            buf.append(c);
            break;
        }
      }
      if (state == 2) {
        throw new IllegalArgumentException(
            String.format("Unclosed csv quote, line: [%s] length: [%d]", line, line.length()));
      }
      result.add(buf.toString());
    }
    return result;
  }

  /**
   * Convert input stream to string
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is
   * @return
   * @throws IOException fromInputStream
   */
  public static String fromInputStream(InputStream is) throws IOException {
    return fromInputStream(is, StandardCharsets.UTF_8);
  }

  /**
   * Convert input stream to string
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is the input stream
   * @param charset
   * @return
   * @throws IOException fromInputStream
   */
  public static String fromInputStream(InputStream is, Charset charset) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (Reader reader = new BufferedReader(new InputStreamReader(is, charset))) {
      int c = 0;
      while ((c = reader.read()) != -1) {
        sb.append((char) c);
      }
    }
    return sb.toString();
  }

  public static <T> Stream<T> lines(final BufferedReader reader, int offset,
      BiPredicate<Integer, String> terminator, Function<String, T> converter) {
    return streamOf(new Iterator<>() {
      final BiPredicate<Integer, String> useTerminator =
          terminator == null ? (i, t) -> false : terminator;
      String nextLine = null;
      int readLines = 0;
      boolean valid = true;
      // skip lines if necessary
      {
        try {
          if (offset > 0) {
            for (int i = 0; i < offset; i++) {
              if (reader.readLine() == null) {
                valid = false;
                break;
              }
            }
          }
        } catch (Exception e) {
          throw new CorantRuntimeException(e);
        }
      }

      @Override
      public boolean hasNext() {
        if (!valid) {
          return false;
        }
        if (nextLine != null) {
          return true;
        } else {
          try {
            nextLine = reader.readLine();
            return nextLine != null && !useTerminator.test(++readLines, nextLine);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }

      @Override
      public T next() {
        if (nextLine != null || hasNext()) {
          String line = nextLine;
          nextLine = null;
          return converter.apply(line);
        } else {
          throw new NoSuchElementException();
        }
      }
    });
  }

  /**
   * String lines from file, use for read text file line by line.
   *
   * @param file the text file
   * @return lines
   */
  public static Stream<String> lines(final File file) {
    FileInputStream fis;
    try {
      fis = new FileInputStream(shouldNotNull(file));
    } catch (FileNotFoundException e1) {
      throw new CorantRuntimeException(e1);
    }
    return lines(fis, -1, -1).onClose(() -> {
      try {
        fis.close();
      } catch (IOException e) {
        throw new CorantRuntimeException(e);
      }
    });
  }

  /**
   * String lines from file input stream, use for read text file line by line.
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param fis the text file input stream
   * @param offset the offset start from 0
   * @param limit the number of lines returned
   */
  public static Stream<String> lines(final FileInputStream fis, int offset, int limit) {
    return lines(new InputStreamReader(fis, StandardCharsets.UTF_8), offset,
        (i, t) -> limit >= 1 && i > limit);
  }

  /**
   * String lines from input stream, use for read text file line by line.
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is the text input stream
   */
  public static Stream<String> lines(final InputStream is) {
    return lines(new InputStreamReader(is, StandardCharsets.UTF_8), 0, null);
  }

  /**
   * String lines from input stream, use for read text line by line.
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is the text input stream
   * @param charset the charset
   * @param offset use to skip lines, the offset start from 0
   * @param terminator use to brake out the stream, terminator return true means need to brake out
   */
  public static Stream<String> lines(final InputStream is, Charset charset, int offset,
      BiPredicate<Integer, String> terminator) {
    return lines(new InputStreamReader(is, charset), offset, terminator);
  }

  /**
   * String lines from input stream, use for read text file line by line.
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is the text input stream
   * @param offset use to skip lines, the offset start from 0
   * @param terminator use to brake out the stream, terminator return true means need to brake out
   */
  public static Stream<String> lines(final InputStream is, int offset,
      BiPredicate<Integer, String> terminator) {
    return lines(new InputStreamReader(is, StandardCharsets.UTF_8), offset, terminator);
  }

  /**
   * String lines from input stream reader, use for read text file line by line.
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param isr the text input stream reader
   * @param offset the offset start from 0
   * @param terminator use to brake out the stream, terminator return true means need to brake out
   */
  public static Stream<String> lines(final InputStreamReader isr, int offset,
      BiPredicate<Integer, String> terminator) {
    final BufferedReader reader = new BufferedReader(isr);
    return lines(reader, offset, terminator, UnaryOperator.identity());
  }

  /**
   * Sting lines from file path.
   *
   * @param filePath the text file path
   * @return lines
   */
  public static Stream<String> lines(final String filePath) {
    return lines(new File(filePath));
  }

  /**
   * Return string lines from file path.
   *
   * @param path
   * @return readFromFile
   */
  public static List<String> readFromFilePath(String path) {
    return Texts.lines(new File(path)).collect(Collectors.toList());
  }

  /**
   * Format objects to CSV line string.
   *
   * @param objects
   * @return toCSVLine
   */
  public static String toCSVLine(Iterable<?> objects) {
    String line = streamOf(objects).map(o -> Objects.asString(o, EMPTY)).map(s -> {
      boolean q =
          s.contains(CSV_FIELD_DELIMITER_STRING) || s.contains(RETURN) || s.contains(NEWLINE);
      String r;
      if (s.contains(CSV_FIELD_QUOTES_STRING)) {
        q = true;
        r = s.replace(CSV_FIELD_QUOTES_STRING, CSV_DOUBLE_QUOTES);
      } else {
        r = s;
      }
      return q ? CSV_FIELD_QUOTES_STRING + r + CSV_FIELD_QUOTES_STRING : r;
    }).collect(Collectors.joining(CSV_FIELD_DELIMITER_STRING));
    return line.endsWith(String.valueOf(CSV_FIELD_DELIMITER)) ? line.substring(0, line.length() - 1)
        : line;
  }

  /**
   * Format objects to CSV line string.
   *
   * @param objects
   * @return toCSVLine
   */
  public static String toCSVLine(Object... objects) {
    return toCSVLine(listOf(objects));
  }

  /**
   * Convert input stream to String
   *
   * Note: The caller must maintain resource release by himself
   *
   * @param is
   * @return tryFromInputStream
   */
  public static String tryFromInputStream(InputStream is) {
    try {
      return fromInputStream(is);
    } catch (IOException e) {
      return null;
    }
  }

  public static void tryWriteToFile(File file, boolean append, Charset charset, String data)
      throws IOException {
    try (OutputStream os = new FileOutputStream(file, append);
        BufferedWriter fileWritter = new BufferedWriter(new OutputStreamWriter(os, charset))) {
      fileWritter.append(data);
    }
  }

  public static void tryWriteToFile(File file, boolean append, String data) throws IOException {
    try (OutputStream os = new FileOutputStream(file, append);
        BufferedWriter fileWritter =
            new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
      fileWritter.append(data);
    }
  }

  public static void tryWriteToFile(File file, Iterable<String> data) {
    try {
      writeToFile(file, false, streamOf(data));
    } catch (IOException e) {
      // Noop!
    }
  }

  public static void writeToFile(File file, boolean append, Charset charset, Stream<String> lines)
      throws IOException {
    if (!file.exists()) {
      shouldBeTrue(file.createNewFile());
    }
    try (OutputStream os = new FileOutputStream(file, append);
        BufferedWriter fileWritter = new BufferedWriter(new OutputStreamWriter(os, charset))) {
      lines.forEach(line -> {
        try {
          fileWritter.append(line);
          fileWritter.newLine();
          fileWritter.flush();
        } catch (Exception e) {
          throw new CorantRuntimeException(e);
        }
      });
    }
  }

  public static void writeToFile(File file, boolean append, Stream<String> lines)
      throws IOException {
    writeToFile(file, append, StandardCharsets.UTF_8, lines);
  }

  /**
   * Write string to file line by line, string lines will be written to the beginning of the file.
   *
   * @param file
   * @param data
   * @throws IOException writeToFile
   */
  public static void writeToFile(File file, Iterable<String> data) throws IOException {
    writeToFile(file, false, streamOf(data));
  }

  /**
   * corant-shared
   *
   * Special reader, used to deal with CSV line breaks.
   *
   * @author bingo 上午11:00:21
   *
   */
  static class CSVBufferedReader extends BufferedReader {

    public CSVBufferedReader(Reader in) {
      super(in);
    }

    @Override
    public String readLine() throws IOException {
      return readCSVLine();
    }

    String readCSVLine() throws IOException {
      StringBuilder result = new StringBuilder(128);
      boolean inquotes = false;
      for (;;) {
        int intRead = read();
        if (intRead == -1) {
          return result.length() == 0 ? null : result.toString();
        }
        char c = (char) intRead;
        if (c == CSV_FIELD_QUOTES) {
          inquotes = !inquotes;
        }
        if ((c == Chars.NEWLINE || c == Chars.RETURN) && !inquotes) {
          break;
        }
        result.append(c);
      }
      return result.toString();
    }

  }
}
