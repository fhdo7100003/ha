package com.github.fhdo7100003.ha;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.github.fhdo7100003.ha.LogMeta.LogFilter;
import com.github.fhdo7100003.ha.Logger.LineFormatter;
import com.github.fhdo7100003.ha.Simulation.InvalidSimulation;
import com.github.fhdo7100003.ha.Simulation.Report;
import com.github.fhdo7100003.ha.Simulation.StaticTimestampGenerator;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import io.javalin.Javalin;

public class Main {
  record Response(UUID id, Report report) {
  }

  public static void main(String[] args) {
    final var logPath = "log";
    final var runner = new SimulationRunner(Path.of(logPath));
    // NOTE: intermediate variable otherwise horrible formatting
    final var javalin = Javalin.create(cfg -> {
      cfg.useVirtualThreads = true;
    });

    javalin
        .put("/simulation", ctx -> {
          final var body = ctx.body();
          final var sim = Simulation.fromJSON(body);
          // TODO: log above sim
          final var res = runner.runSimulation(sim).join();
          final var report = res.getOk();
          if (report != null) {
            // NOTE: should probably not create a new thing
            // for every request
            final var gson = new Gson();

            ctx.result(gson.toJson(report));
          } else {
            ctx.status(500);
            ctx.result(res.getErr().toString());
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

  static record SimulationRunner(Path logRoot) {
    CompletableFuture<Result<Response, IOException>> runSimulation(final Simulation sim) {
      final var ret = new CompletableFuture<Result<Response, IOException>>();
      Thread.ofPlatform().start(() -> {
        final var id = UUID.randomUUID();
        final var gen = new StaticTimestampGenerator();
        try (final var logger = Logger.open(logRoot.resolve(id.toString()), new LineFormatter(), gen)) {
          final var res = sim.run(logger);
          ret.complete(Result.ok(new Response(id, res)));
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
