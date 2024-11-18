package com.github.fhdo7100003.ha;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class CalendarDeserializer implements JsonDeserializer<Calendar> {
  @Override
  public Calendar deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    final var parsed = Instant.parse(json.getAsString());
    final var dt = ZonedDateTime.ofInstant(parsed, ZoneId.systemDefault());
    return GregorianCalendar.from(dt);
  }
}
