package com.github.fhdo7100003.ha;

import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;

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
  public static class Result<T, E> {
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

}
