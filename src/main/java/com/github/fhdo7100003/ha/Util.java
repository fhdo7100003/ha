package com.github.fhdo7100003.ha;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class Util {
  public static boolean hasExtension(@NotNull final Path path, @NotNull final String extension) {
    if (!extension.startsWith(".")) {
      throw new RuntimeException(String.format("%s is not a valid file extension, missing leading dot"));
    }
    // NOTE: Path.endsWith is ridiculous and actually checks if the given
    // Path has a specific filename
    return path.toString().endsWith(extension);
  }

  // this sucks
  public static final class Result<T, E> {
    private T res;
    private E err;

    private Result(T res, E err) {
      this.res = res;
      this.err = err;
    }

    public static <T, E> Result<T, E> ok(T res) {
      return new Result<T, E>(res, null);
    }

    public static <T, E> Result<T, E> err(E err) {
      return new Result<T, E>(null, err);
    }

    public T getOk() {
      return this.res;
    }

    public E getErr() {
      return this.err;
    }
  }

  public static final class FusedInputStreamBuilder implements Closeable {
    List<InputStream> sources;

    FusedInputStreamBuilder() {
      sources = new ArrayList<>();
    }

    public FusedInputStreamBuilder add(@NotNull final InputStream s) {
      sources.add(s);
      return this;
    }

    public @NotNull FusedInputStream build() {
      // NOTE: important to clear the sources, otherwise
      // annoying to use with try-with-ressources
      final var ret = new FusedInputStream(sources);
      // NOTE: don't clear the original one because pass
      // by reference, just get a new list
      sources = new ArrayList<>();
      return ret;
    }

    @Override
    public void close() throws IOException {
      closeAsManyAsPossible(sources);
    }
  }

  private static void closeAsManyAsPossible(List<InputStream> sources) throws IOException {
    // try to close as many inputstreams as possible
    // and ignore everything except the last exception
    IOException lastException = null;
    for (final var src : sources) {
      try {
        src.close();
      } catch (IOException e) {
        lastException = e;
      }
    }
    if (lastException != null) {
      throw lastException;
    }
  }

  public static final class FusedInputStream extends InputStream {
    final List<InputStream> sources;
    int currentPos = 0;

    private FusedInputStream(@NotNull final List<InputStream> sources) {
      this.sources = sources;
    }

    public static @NotNull FusedInputStreamBuilder builder() {
      return new FusedInputStreamBuilder();
    }

    @Override
    public int read() throws IOException {
      final var tmp = new byte[1];
      // FIXME: what happens if it reads 0 bytes?
      return read(tmp) == -1 ? -1 : (int) tmp[0];
    }

    @Override
    public int read(byte[] buf) throws IOException {
      while (true) {
        if (currentPos == sources.size()) {
          return -1;
        }
        final var current = sources.get(currentPos);
        final var read = current.read(buf);

        if (read == -1) {
          currentPos++;
        } else {
          return read;
        }
      }
    }

    @Override
    public void close() throws IOException {
      closeAsManyAsPossible(sources);
    }
  }

}
