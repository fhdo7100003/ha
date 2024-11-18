package com.github.fhdo7100003.ha;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.github.fhdo7100003.ha.Logger.LineFormatter;
import com.github.fhdo7100003.ha.Util.Result;
import static com.github.fhdo7100003.ha.Util.hasExtension;
import com.github.fhdo7100003.ha.Simulation.Report;
import com.github.fhdo7100003.ha.Simulation.StaticTimestampGenerator;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import io.javalin.http.Context;
import io.javalin.util.JavalinLogger;

public final class ApiController {
  final Path logPath;
  final SimulationRunner runner;

  public ApiController(@NotNull final Path logPath) {
    this.logPath = logPath;
    this.runner = new SimulationRunner(logPath);
  }

  private record SimulationResult(UUID id, Report report) {
  }

  private static record FinishedSimulation(Path root) {
    public @NotNull Report readResult() throws IOException, JsonParseException {
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

  public void getAllSimulations(@NotNull final Context ctx) throws IOException {
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

    record Response(List<String> devices, Report res) {
    }

    try {
      ctx.json(new Response(new ArrayList<>(sim.readDevices()), sim.readResult()));
    } catch (JsonParseException e) {
      JavalinLogger.error("Invalid saved json for simulation", e);
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
