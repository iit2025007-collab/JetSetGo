package com.jetsetgo.service.fare;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public final class PremiumEconomyFarePolicy extends AbstractFarePolicy {
    public PremiumEconomyFarePolicy() {
        super("PREMIUM_ECONOMY");
    }

    @Override
    protected BigDecimal classMultiplier() {
        return new BigDecimal("1.5");
    }
}
