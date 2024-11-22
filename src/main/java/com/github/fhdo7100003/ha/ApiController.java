package com.github.fhdo7100003.ha;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.github.fhdo7100003.ha.Logger.LineFormatter;
import com.github.fhdo7100003.ha.Util.FusedInputStream;
import com.github.fhdo7100003.ha.Util.Result;

import static com.github.fhdo7100003.ha.Main.Gson;
import static com.github.fhdo7100003.ha.Util.hasExtension;
import com.github.fhdo7100003.ha.Simulation.Report;
import com.github.fhdo7100003.ha.Simulation.StaticTimestampGenerator;
import com.google.gson.JsonParseException;

import io.javalin.http.Context;
import io.javalin.util.JavalinLogger;

public final class ApiController {
  final Path logPath;
  final SimulationRunner runner;

  private static final String ALL_LOG_FILENAME = "all.txt";
  private static final String RESULT_FILENAME = "result.json";
  private static final String SOURCE_FILENAME = "source.json";

  public ApiController(@NotNull final Path logPath) {
    this.logPath = logPath;
    this.runner = new SimulationRunner(logPath);
  }

  private record SimulationResult(UUID id, Report report) {
  }

  private static record FinishedSimulation(Path root) {
    public @NotNull Report readResult() throws IOException, JsonParseException {
      return Gson.fromJson(Files.newBufferedReader(root.resolve(RESULT_FILENAME)), Report.class);
    }

    public @NotNull Set<String> readDevices() throws IOException {
      try (var list = Files.list(root)) {
        return list
            .filter(entry -> {
              return !entry.getFileName().toString().equals(ALL_LOG_FILENAME) && hasExtension(entry, ".txt");
            })
            .map(entry -> LogMeta.parse(entry.getFileName().toString()).deviceName())
            .collect(Collectors.toSet());
      }
    }

    public @NotNull InputStream getLog() throws IOException {
      return new BufferedInputStream(Files.newInputStream(root.resolve(ALL_LOG_FILENAME)));
    }

    public @NotNull InputStream readDeviceLog(@NotNull final String deviceName) throws IOException {
      // NOTE: probably better to make some sort of fusedinputstream that concats the
      // inputstreams instead
      try (final var ret = FusedInputStream.builder();
          final var list = Files.list(root)) {

        final var deviceLogs = list
            .filter(entry -> {
              if (entry.getFileName().equals(Path.of(ALL_LOG_FILENAME)) || !hasExtension(entry, ".txt")) {
                return false;
              }
              final var meta = LogMeta.parse(entry.getFileName().toString());
              return meta.deviceName().equals(deviceName);
            })
            .collect(Collectors.toList());
        deviceLogs.sort((a, b) -> a.compareTo(b));

        for (final var entry : deviceLogs) {
          ret.add(Files.newInputStream(entry));
        }

        return new BufferedInputStream(ret.build());
      }
    }

    public @NotNull InputStream getSource() throws IOException {
      return new BufferedInputStream(Files.newInputStream(root.resolve(SOURCE_FILENAME)));
    }
  }

  private static record SimulationRunner(Path logRoot) {
    CompletableFuture<Result<SimulationResult, IOException>> runSimulation(final String source, final Simulation sim) {
      final var ret = new CompletableFuture<Result<SimulationResult, IOException>>();
      Thread.ofPlatform().start(() -> {
        final var id = UUID.randomUUID();
        final var gen = new StaticTimestampGenerator();
        final var resultDir = logRoot.resolve(id.toString());
        try (final var logger = Logger.open(resultDir, new LineFormatter(), gen)) {
          final var res = sim.run(logger);
          final var resp = new SimulationResult(id, res);
          Files.writeString(resultDir.resolve(RESULT_FILENAME), Gson.toJson(resp.report()));
          Files.writeString(resultDir.resolve(SOURCE_FILENAME), source);
          ret.complete(Result.ok(resp));
        } catch (IOException e) {
          ret.complete(Result.err(e));
        }
      });
      return ret;
    }
  }

  public void getAllSimulations(@NotNull final Context ctx) throws IOException {
    try (var list = Files.list(logPath)) {
      record Field(long timestamp, UUID id) {
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
  }

  public void runSimulation(@NotNull final Context ctx) throws IOException {
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
  }

  public void getSimulation(@NotNull final Context ctx) throws IOException {
    final var uuid = ctx.pathParam("uuid");
    final var sim = new FinishedSimulation(logPath.resolve(uuid));

    record Response(Set<String> devices, Report res) {
    }

    try {
      ctx.json(new Response(sim.readDevices(), sim.readResult()));
    } catch (JsonParseException e) {
      JavalinLogger.error("Invalid saved json for simulation", e);
      ctx.status(500);
    }
  }

  public void getSimulationSource(@NotNull final Context ctx) throws IOException {
    final var uuid = ctx.pathParam("uuid");
    final var sim = new FinishedSimulation(logPath.resolve(uuid));
    ctx.contentType("application/json").result(sim.getSource());
  }

  public void getSimulationLog(@NotNull final Context ctx) throws IOException {
    final var uuid = ctx.pathParam("uuid");
    final var sim = new FinishedSimulation(logPath.resolve(uuid));
    ctx.result(sim.getLog());
  }

  public void getSimulationLogBySensorName(@NotNull final Context ctx) throws IOException {
    final var uuid = ctx.pathParam("uuid");
    final var sensorName = ctx.pathParam("sensorName");
    final var sim = new FinishedSimulation(logPath.resolve(uuid));
    ctx.result(sim.readDeviceLog(sensorName));
  }
}
