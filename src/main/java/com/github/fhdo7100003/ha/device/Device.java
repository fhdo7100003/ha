package com.github.fhdo7100003.ha.device;

import java.lang.reflect.Type;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.stream.StreamSupport;

import com.github.fhdo7100003.ha.Logger;

public abstract class Device {
  final private String name;

  public String getName() {
    return name;
  }

  public Device(String name) {
    try {
      // only allow names viable in filenames
      final var path = Path.of(name);

      if (path.isAbsolute()) {
        throw new InvalidDeviceName("Device name is an absolute path");
      } else if (StreamSupport.stream(path.spliterator(), false).count() != 1) {
        throw new InvalidDeviceName("Device name is a relative path");
      }

    } catch (InvalidPathException e) {
      throw new InvalidDeviceName(String.format("Invalid device name %s, not usable in filenames"), e);
    }
    this.name = name;
  }

  public final int tick(final Calendar currentTime, final Logger logger) {
    final var v = innerTick(currentTime);
    if (v != 0) {
      logger.logDevice(this, v < 0 ? "consumed" : "produced", "value", v);
    }
    return v;
  }

  protected abstract int innerTick(final Calendar currentTime);

  public static class InvalidDeviceName extends RuntimeException {
    public InvalidDeviceName(final String msg) {
      super(msg);
    }

    public InvalidDeviceName(final String msg, final Throwable err) {
      super(msg, err);
    }
  }
}
