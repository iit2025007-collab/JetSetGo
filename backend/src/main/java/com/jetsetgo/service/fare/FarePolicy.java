package com.jetsetgo.service.fare;

import java.math.BigDecimal;

public interface FarePolicy {
    String travelClass();

    BigDecimal multiplier();

    BigDecimal calculate(BigDecimal baseFare, int passengerCount);
}
