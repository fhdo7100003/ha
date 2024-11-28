package com.github.fhdo7100003.ha;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import com.github.fhdo7100003.ha.device.Device;

import java.text.SimpleDateFormat;

public final class Logger implements Closeable {
  final private Path directory;
  final private ConcurrentMap<String, Entry> sinks;
  final private Actor.Ref mainLog;
  final private Formatter fmt;
  final public static SimpleDateFormat YMD = new SimpleDateFormat("yyyy-MM-dd");
  final private TimestampGenerator timestampGenerator;

  public static interface Formatter {
    String format(final Calendar date, final String message, final Object[] attrs);
  }

  public static final class LineFormatter implements Formatter {
    final private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

    public String format(final Calendar date, final String message, final Object[] attrs) {
      final var buf = new StringBuilder();
      buf.append(dateFormat.format(date.getTime()));
      buf.append(": ");
      buf.append(message);
      if (attrs.length > 0) {
        buf.append(" ");
      }

      boolean writeEq = true;
      for (int i = 0; i < attrs.length; i++) {
        final var attr = attrs[i];
        buf.append(attr.toString().replace("\n", " "));
        if (writeEq && !(i == attrs.length - 1)) {
          buf.append("=");
        } else if (i < attrs.length - 1) {
          buf.append(",");
        }
        writeEq = !writeEq;
      }

      buf.append("\n");

      return buf.toString();
    }
  }

  public static interface TimestampGenerator {
    Calendar now();
  }

  public static class RealtimeGenerator implements TimestampGenerator {
    @Override
    public Calendar now() {
      return Calendar.getInstance();
    }
  }

  private static record Entry(Actor.Ref sink, int date) {
  }

  public static Logger open(final Path directory, final Formatter fmt, final TimestampGenerator gen)
      throws IOException {
    Files.createDirectories(directory);
    final var mainLog = LoggingSink.open(directory.resolve("all.txt"));
    return new Logger(directory, fmt, gen, mainLog);
  }

  private Logger(final Path directory, final Formatter fmt, final TimestampGenerator timestampGenerator,
      final LoggingSink mainLog) {
    this.directory = directory;
    this.fmt = fmt;
    this.mainLog = Actor.spawn(new LoggingSinkActor(mainLog), 32);
    this.sinks = new ConcurrentHashMap<>();
    this.timestampGenerator = timestampGenerator;
  }

  private Path getPath(final Calendar date, final String deviceName) {
    return directory.resolve(String.format("%s_%s.txt", YMD.format(date.getTime()), deviceName));
  }

  public TimestampGenerator getTimestampGenerator() {
    return timestampGenerator;
  }

  public void log(final String message, Object... attrs) {
    final var now = timestampGenerator.now();
    final var buf = fmt.format(now, message, attrs);
    mainLog.tell(new WriteEntry(buf));
  }

  public void logDevice(final Device device, final String message, Object... attrs) {
    final var now = timestampGenerator.now();
    final var deviceName = device.getName();
    Entry entry = sinks.get(deviceName);
    final var today = now.get(Calendar.DAY_OF_YEAR);
    Actor.Ref sink;
    if (entry == null || today != entry.date) {
      final var entryPath = getPath(now, deviceName);
      try {
        Entry oldEntry;
        sink = Actor.spawn(new LoggingSinkActor(LoggingSink.open(entryPath)), 32);
        if ((oldEntry = sinks.put(deviceName, new Entry(sink, today))) != null) {
          final var res = oldEntry.sink.ask(new Close());
          if (res != null) {
            throw res;
          }
        }
      } catch (IOException e) {
        System.out.printf("Failed opening %s: %s\n", entryPath, e);
        return;
      }
    } else {
      sink = entry.sink;
    }

    // FIXME: probably better way to do this
    final var args = new ArrayList<Object>();
    args.add("device");
    args.add(deviceName);
    for (final var field : attrs) {
      args.add(field);
    }

    final var buf = fmt.format(now, message, args.toArray());
    sink.tell(new WriteEntry(buf));

    mainLog.tell(new WriteEntry(buf));
  }

  @Override
  public void close() throws IOException {
    Consumer<Actor.Ref> closeSink = (a) -> {
      final var ret = a.ask(new Close());
      if (ret != null) {
        System.out.printf("Failed closing output: %s\n", ret);
      }
    };
    for (Entry v : sinks.values()) {
      closeSink.accept(v.sink);
    }

    closeSink.accept(mainLog);
  }
}
