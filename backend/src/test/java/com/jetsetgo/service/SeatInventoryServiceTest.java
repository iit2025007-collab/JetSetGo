package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SeatInventoryServiceTest {
    @Test
    void assignsConfiguredCabinBlocksWithoutUnnecessarilyExpandingSmallAircraft() {
        assertEquals("FIRST", SeatInventoryService.cabinClass(0, 48));
        assertEquals("BUSINESS", SeatInventoryService.cabinClass(6, 48));
        assertEquals("PREMIUM_ECONOMY", SeatInventoryService.cabinClass(18, 48));
        assertEquals("ECONOMY", SeatInventoryService.cabinClass(24, 48));
        assertEquals("ECONOMY", SeatInventoryService.cabinClass(0, 18));
        assertEquals("ECONOMY", SeatInventoryService.cabinClass(0, 24));
        assertEquals("ECONOMY", SeatInventoryService.cabinClass(24, 30));
    }
}
