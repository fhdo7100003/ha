package com.github.fhdo7100003.ha.device;

import java.util.Calendar;

import com.github.fhdo7100003.ha.Logger;

public final class Store extends Device {
  private int currentCapacity = 0;
  final private int maxCapacity;
  final private int maxChargePerTick;

  public Store(final String name, final int maxCapacity, final int maxChargePerTick) {
    super(name);
    this.maxCapacity = maxCapacity;
    this.maxChargePerTick = maxChargePerTick;
  }

  public final int tryCharge(int capacity) {
    final var isCharging = capacity >= 0;
    capacity = Math.abs(capacity);
    final var cappedCapacity = Math.min(maxChargePerTick, capacity);
    // TODO: care about overflow
    final var endCapacity = isCharging ? Math.min(currentCapacity + cappedCapacity, maxCapacity)
        : Math.max(currentCapacity - cappedCapacity, 0);
    final var diff = Math.abs(endCapacity - currentCapacity);
    currentCapacity = endCapacity;
    return diff;
  }

  @Override
  protected int innerTick(final Calendar currentTime) {
    return 0;
  }
}
