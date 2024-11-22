package com.github.fhdo7100003.ha.deserializers;

import java.lang.reflect.Type;

import com.github.fhdo7100003.ha.device.Device;
import com.github.fhdo7100003.ha.device.SolarPanel;
import com.github.fhdo7100003.ha.device.StableDevice;
import com.github.fhdo7100003.ha.device.Store;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class DeviceSerializer implements JsonDeserializer<Device> {
  private static JsonElement forceField(final JsonObject obj, final String field) throws JsonParseException {
    final var ret = obj.get(field);
    if (ret == null) {
      throw new JsonParseException(String.format("Missing field %s in %s", field, obj));
    }

    return ret;
  }

  private static <T> JsonElement forcePrimitiveField(final JsonObject obj, final String field, final Class<T> c) {
    final var ret = forceField(obj, field);
    final var prim = ret.getAsJsonPrimitive();

    String err = null;
    if (c == String.class) {
      if (!prim.isString()) {
        err = "Expected string";
      }
    } else if (c == Integer.class) {
      if (!prim.isNumber()) {
        err = "Expected integer";
      }
    }

    if (err != null) {
      throw new JsonParseException(String.format("%s for field %s: Got %s", err, field, prim));
    }

    return ret;
  }

  @Override
  public Device deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject deviceObj = json.getAsJsonObject();
    final var name = forcePrimitiveField(deviceObj, "name", String.class).getAsString();
    final var type = forcePrimitiveField(deviceObj, "type", String.class).getAsString();
    switch (type) {
      case "StableDevice" -> {
        return new StableDevice(name, forcePrimitiveField(deviceObj, "produces", Integer.class).getAsInt());
      }
      case "Store" -> {
        final var capacity = forcePrimitiveField(deviceObj, "maxCapacity", Integer.class)
            .getAsInt();
        final var chargePerTick = forcePrimitiveField(deviceObj, "maxChargePerTick", Integer.class)
            .getAsInt();
        return new Store(name, capacity, chargePerTick);
      }
      case "SolarPanel" -> {
        return new SolarPanel(name);
      }
      default -> {
        throw new JsonParseException(String.format("Unknown type %s", type));
      }
    }
  }
}
