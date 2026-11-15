package com.jetsetgo.service.fare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FarePolicyResolverTest {
    private final FarePolicyResolver fares = new FarePolicyResolver(List.of(
            new EconomyFarePolicy(), new PremiumEconomyFarePolicy(),
            new BusinessFarePolicy(), new FirstClassFarePolicy()));

    @Test
    void dispatchesClassSpecificFareStrategies() {
        BigDecimal baseFare = new BigDecimal("1000");

        assertEquals(new BigDecimal("1000.00"), fares.calculate("ECONOMY", baseFare, 1));
        assertEquals(new BigDecimal("1500.00"), fares.calculate("premium_economy", baseFare, 1));
        assertEquals(new BigDecimal("5000.00"), fares.calculate("BUSINESS", baseFare, 2));
        assertEquals(new BigDecimal("4000.00"), fares.calculate("FIRST", baseFare, 1));
    }

    @Test
    void rejectsUnsupportedCabins() {
        assertThrows(ResponseStatusException.class, () -> fares.resolve("STUDIO"));
    }
}
