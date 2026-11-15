package com.jetsetgo.service.fare;

import java.math.BigDecimal;

public abstract class AbstractFarePolicy implements FarePolicy {
    private final String travelClass;

    protected AbstractFarePolicy(String travelClass) {
        this.travelClass = travelClass;
    }

    @Override
    public final String travelClass() {
        return travelClass;
    }

    @Override
    public final BigDecimal multiplier() {
        return classMultiplier();
    }

    @Override
    public final BigDecimal calculate(BigDecimal baseFare, int passengerCount) {
        return baseFare.multiply(BigDecimal.valueOf(passengerCount)).multiply(multiplier());
    }

    protected abstract BigDecimal classMultiplier();
}
