package com.github.fhdo7100003.ha;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.github.fhdo7100003.ha.LogMeta.LogFilter;
import com.github.fhdo7100003.ha.Logger.LineFormatter;
import com.github.fhdo7100003.ha.Simulation.InvalidSimulation;
import com.github.fhdo7100003.ha.Simulation.Report;
import com.github.fhdo7100003.ha.Simulation.StaticTimestampGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import io.javalin.Javalin;
import io.javalin.json.JsonMapper;

public class Main {
  record SimulationResult(UUID id, Report report) {
  }

  public static void main(String[] args) {
    final var logPath = Path.of("log");
    final var runner = new SimulationRunner(logPath);
    Gson gson = new GsonBuilder().create();
    JsonMapper gsonMapper = new JsonMapper() {
      @Override
      public String toJsonString(@NotNull Object obj, @NotNull Type type) {
        return gson.toJson(obj, type);
      }

      @Override
      public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
        return gson.fromJson(json, targetType);
      }
    };

    // NOTE: intermediate variable otherwise horrible formatting
    final var javalin = Javalin.create(cfg -> {
      cfg.useVirtualThreads = true;
      cfg.jsonMapper(gsonMapper);
    });

    javalin
        .put("/simulation", ctx -> {
          final var body = ctx.body();
          final var sim = Simulation.fromJSON(body);
          // TODO: log above sim
          final var res = runner.runSimulation(body, sim).join();
          final var report = res.getOk();
          if (report != null) {
            ctx.json(report);
          } else {
            ctx.status(500);
            ctx.result(res.getErr().toString());
          }
        })
        .get("/simulation", ctx -> {
          try (var list = Files.list(logPath)) {
            record Field(long timestamp, UUID simulations) {
            }

            final var simulations = list.map(entry -> {
              final var uuid = UUID.fromString(entry.getFileName().toString());
              try {
                final BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                return new Field(attrs.creationTime().toMillis(), uuid);
                // ugh
              } catch (IOException e) {
                throw new InternalError(e);
              }
            }).collect(Collectors.toList());
            ctx.json(simulations);
          }
        })
        .get("/simulation/{uuid}", ctx -> {
          final var uuid = ctx.pathParam("uuid");
          final var sim = new FinishedSimulation(logPath.resolve(uuid));

          record Response(Set<String> devices, Report res) {
          }

          try {
            ctx.json(new Response(sim.readDevices(), sim.readResult()));
          } catch (IOException e) {
            throw new InternalError(e);
          }
        })
        .get("/simulation/{uuid}/source", ctx -> {
          final var uuid = ctx.pathParam("uuid");
          final var sim = new FinishedSimulation(logPath.resolve(uuid));
          try {
            ctx.contentType("application/json").result(sim.getSource());
          } catch (IOException e) {
            throw new InternalError(e);
          }
        })
        .get("/simulation/{uuid}/log", ctx -> {
          final var uuid = ctx.pathParam("uuid");
          final var sim = new FinishedSimulation(logPath.resolve(uuid));
          try {
            ctx.result(sim.getLog());
          } catch (IOException e) {
            throw new InternalError();
          }
        })
        .get("/simulation/{uuid}/log/{sensorName}", ctx -> {
          final var uuid = ctx.pathParam("uuid");
          final var sensorName = ctx.pathParam("sensorName");
          final var sim = new FinishedSimulation(logPath.resolve(uuid));
          try {
            ctx.result(sim.readDeviceLog(sensorName));
          } catch (IOException e) {
            throw new InternalError();
          }
        })
        .exception(JsonParseException.class, (e, ctx) -> ctx.status(400))
        .exception(InvalidSimulation.class, (e, ctx) -> ctx.status(400))
        .start(8000);
  }

  // this sucks
  static class Result<T, E> {
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

  static boolean hasExtension(@NotNull final Path path, @NotNull final String extension) {
    if (!extension.startsWith(".")) {
      throw new RuntimeException(String.format("%s is not a valid file extension, missing leading dot"));
    }
    // NOTE: Path.endsWith is ridiculous and actually checks if the given
    // Path has a specific filename
    return path.toString().endsWith(extension);
  }

  static record FinishedSimulation(Path root) {
    public @NotNull Report readResult() throws IOException {
      return new Gson().fromJson(Files.newBufferedReader(root), Report.class);
    }

    public @NotNull Set<String> readDevices() throws IOException {
      try (var list = Files.list(root)) {
        return list
            .filter(entry -> {
              return !entry.getFileName().toString().equals("all.txt") && hasExtension(entry, ".txt");
            })
            .map(entry -> LogMeta.parse(entry.getFileName().toString()).deviceName())
            .collect(Collectors.toSet());
      }
    }

    public @NotNull InputStream getLog() throws IOException {
      return new BufferedInputStream(Files.newInputStream(root.resolve("all.txt")));
    }

    public @NotNull String readDeviceLog(@NotNull final String deviceName) throws IOException {
      // NOTE: probably better to make some sort of fusedinputstream that concats the
      // inputstreams instead
      final var sb = new StringBuilder();
      try (var list = Files.list(root)) {
        final var deviceLogs = list
            .filter(entry -> {
              if (entry.getFileName().equals(Path.of("all.txt")) || !hasExtension(entry, ".txt")) {
                return false;
              }
              final var meta = LogMeta.parse(entry.getFileName().toString());
              return meta.deviceName().equals(deviceName);
            })
            .collect(Collectors.toList());
        deviceLogs.sort((a, b) -> a.compareTo(b));

        for (final var entry : deviceLogs) {
          sb.append(Files.readString(entry));
        }
      }

      return sb.toString();
    }

    public @NotNull InputStream getSource() throws IOException {
      return new BufferedInputStream(Files.newInputStream(root.resolve("source.json")));
    }
  }

  static record SimulationRunner(Path logRoot) {
    CompletableFuture<Result<SimulationResult, IOException>> runSimulation(final String source, final Simulation sim) {
      final var ret = new CompletableFuture<Result<SimulationResult, IOException>>();
      Thread.ofPlatform().start(() -> {
        final var id = UUID.randomUUID();
        final var gen = new StaticTimestampGenerator();
        final var resultDir = logRoot.resolve(id.toString());
        try (final var logger = Logger.open(resultDir, new LineFormatter(), gen)) {
          final var res = sim.run(logger);
          final var resp = new SimulationResult(id, res);
          Files.writeString(resultDir.resolve("result.json"), new Gson().toJson(resp.report()));
          Files.writeString(resultDir.resolve("source.json"), source);
          ret.complete(Result.ok(resp));
        } catch (IOException e) {
          ret.complete(Result.err(e));
        }
      });
      return ret;
    }
  }

  static void showLogs(final Path directory, final LogFilter filter) throws IOException {
    try (var list = Files.list(directory)) {
      final var it = list.iterator();
      while (it.hasNext()) {
        final var path = it.next();
        final var fileName = path.getFileName().toString();
        final var meta = LogMeta.parse(fileName);
        if (meta != null && filter.matches(meta)) {
          final var content = Files.readString(path);
          System.out.printf("Log file from %s\n%s", Logger.YMD.format(meta.date().getTime()),
              content);
        }
      }
    }
  }
}
