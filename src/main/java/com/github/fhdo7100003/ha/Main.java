package com.github.fhdo7100003.ha;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Calendar;

import org.jetbrains.annotations.NotNull;

import com.github.fhdo7100003.ha.Simulation.InvalidSimulation;
import com.github.fhdo7100003.ha.device.Device;
import com.github.fhdo7100003.ha.deserializers.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import io.javalin.Javalin;
import io.javalin.json.JsonMapper;
import io.javalin.util.JavalinLogger;

public class Main {
  public static Gson Gson = new GsonBuilder()
      .registerTypeAdapter(Device.class, new DeviceSerializer())
      .registerTypeAdapter(Calendar.class, new CalendarDeserializer())
      .create();

  public static void main(String[] args) {
    JsonMapper gsonMapper = new JsonMapper() {
      @Override
      public String toJsonString(@NotNull Object obj, @NotNull Type type) {
        return Gson.toJson(obj, type);
      }

      @Override
      public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
        return Gson.fromJson(json, targetType);
      }
    };

    // NOTE: intermediate variable otherwise horrible formatting
    final var javalin = Javalin.create(cfg -> {
      cfg.useVirtualThreads = true;
      cfg.jsonMapper(gsonMapper);
    });

    final var apiController = new ApiController(Path.of("log"));

    javalin
        .put("/simulation", apiController::runSimulation)
        .get("/simulation", apiController::getAllSimulations)
        .get("/simulation/{uuid}", apiController::getSimulation)
        .get("/simulation/{uuid}/source", apiController::getSimulationSource)
        .get("/simulation/{uuid}/log", apiController::getSimulationLog)
        .get("/simulation/{uuid}/log/{sensorName}", apiController::getSimulationLogBySensorName)
        .exception(IOException.class, (e, ctx) -> {
          JavalinLogger.error("Something exploded", e);
          ctx.status(500);
        })
        .exception(JsonParseException.class, (e, ctx) -> ctx.status(400))
        .exception(InvalidSimulation.class, (e, ctx) -> {
          JavalinLogger.info("Received invalid simulation", e);
          ctx.status(400);
        })
        .start(8000);
  }
}
