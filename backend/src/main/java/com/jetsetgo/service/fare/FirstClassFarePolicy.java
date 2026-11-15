package com.jetsetgo.service.fare;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public final class FirstClassFarePolicy extends AbstractFarePolicy {
    public FirstClassFarePolicy() {
        super("FIRST");
    }

    @Override
    protected BigDecimal classMultiplier() {
        return new BigDecimal("4.0");
    }
}
