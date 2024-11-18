package com.github.fhdo7100003.ha.device;

import static com.github.fhdo7100003.ha.Util.forcePrimitiveField;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class DeviceSerializer implements JsonDeserializer<Device> {
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
