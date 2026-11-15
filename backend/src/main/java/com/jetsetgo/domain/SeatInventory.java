package com.jetsetgo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("seat_inventory")
@CompoundIndex(name = "seat_availability_idx", def = "{'flightId': 1, 'available': 1, 'seatNumber': 1}")
public record SeatInventory(@Id String id, String flightId, String seatNumber, boolean available,
                            String cabinClass, String reservationId) {
    public SeatInventory(String id, String flightId, String seatNumber, boolean available) {
        this(id, flightId, seatNumber, available, null, null);
    }

    public static String idFor(String flightId, String seatNumber) {
        return flightId + ":" + seatNumber;
    }
}
