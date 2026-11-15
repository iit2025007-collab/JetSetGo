package com.jetsetgo.service.fare;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import org.springframework.stereotype.Component;

@Component
public final class BusinessFarePolicy extends AbstractFarePolicy {
    public BusinessFarePolicy() {
        super("BUSINESS");
    }

    @Override
    protected BigDecimal classMultiplier() {
        return new BigDecimal("2.5");
    }
}
