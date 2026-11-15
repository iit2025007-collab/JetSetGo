package com.jetsetgo.service.fare;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public final class EconomyFarePolicy extends AbstractFarePolicy {
    public EconomyFarePolicy() {
        super("ECONOMY");
    }

    @Override
    protected BigDecimal classMultiplier() {
        return BigDecimal.ONE;
    }
}
