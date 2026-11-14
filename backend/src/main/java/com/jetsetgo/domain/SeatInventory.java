package com.jetsetgo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("seat_inventory")
public record SeatInventory(@Id String id, String flightId, String seatNumber, boolean available) {
    public static String idFor(String flightId, String seatNumber) {
        return flightId + ":" + seatNumber;
    }
}
