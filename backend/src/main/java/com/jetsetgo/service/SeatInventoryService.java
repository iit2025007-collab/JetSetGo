package com.jetsetgo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.SeatInventoryRepository;

@Service
public class SeatInventoryService {
    private final MongoTemplate mongo;
    private final SeatInventoryRepository seats;

    public SeatInventoryService(MongoTemplate mongo, SeatInventoryRepository seats) {
        this.mongo = mongo;
        this.seats = seats;
    }

    public void ensureInitialized(Flight flight) {
        List<SeatInventory> inventory = new ArrayList<>(flight.capacity());
        for (int index = 0; index < flight.capacity(); index++) {
            String number = (index / 6 + 1) + String.valueOf((char) ('A' + index % 6));
            inventory.add(new SeatInventory(SeatInventory.idFor(flight.id(), number),
                    flight.id(), number, true, cabinClass(index, flight.capacity()), null));
        }
        if (inventory.isEmpty()) {
            return;
        }
        BulkOperations writes = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, SeatInventory.class);
        for (SeatInventory seat : inventory) {
            writes.upsert(Query.query(Criteria.where("_id").is(seat.id())),
                    new Update().setOnInsert("flightId", seat.flightId())
                            .setOnInsert("seatNumber", seat.seatNumber())
                            .setOnInsert("available", true)
                            .setOnInsert("cabinClass", seat.cabinClass()));
            writes.updateOne(Query.query(Criteria.where("_id").is(seat.id())
                            .and("cabinClass").exists(false)),
                    new Update().set("cabinClass", seat.cabinClass()));
        }
        writes.execute();
    }

    public List<String> availableSeats(Flight flight, String travelClass) {
        ensureInitialized(flight);
        String normalizedClass = normalizeClass(travelClass);
        return seats.findByFlightIdAndAvailableTrueOrderBySeatNumber(flight.id()).stream()
                .filter(seat -> normalizedClass.equalsIgnoreCase(
                        seat.cabinClass() == null ? "ECONOMY" : seat.cabinClass()))
                .map(SeatInventory::seatNumber)
                .sorted(Comparator.comparingInt(SeatInventoryService::rowNumber)
                        .thenComparing(seat -> seat.charAt(seat.length() - 1)))
                .toList();
    }

    public void release(String flightId, String seatNumber, String reservationId) {
        Criteria owner = new Criteria().orOperator(
                Criteria.where("reservationId").is(reservationId),
                Criteria.where("reservationId").exists(false),
                Criteria.where("reservationId").is(null));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(SeatInventory.idFor(flightId, seatNumber))
                        .andOperator(owner)),
                new Update().set("available", true).unset("reservationId"), SeatInventory.class);
    }

    public void reserve(String flightId, String seatNumber, String travelClass, String reservationId) {
        String normalizedClass = normalizeClass(travelClass);
        SeatInventory reserved = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(SeatInventory.idFor(flightId, seatNumber))
                        .and("available").is(true).and("cabinClass").is(normalizedClass)),
                new Update().set("available", false).set("reservationId", reservationId),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                SeatInventory.class);
        if (reserved == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat " + seatNumber + " is unavailable in " + normalizedClass.replace('_', ' ') + " class");
        }
    }

    static String cabinClass(int index, int capacity) {
        if (capacity < 30) {
            return "ECONOMY";
        }
        int row = index / 6 + 1;
        if (row == 1) return "FIRST";
        if (row <= 3) return "BUSINESS";
        if (row == 4) return "PREMIUM_ECONOMY";
        return "ECONOMY";
    }

    private static String normalizeClass(String travelClass) {
        if (travelClass == null || travelClass.isBlank()) {
            return "ECONOMY";
        }
        return travelClass.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static int rowNumber(String seat) {
        return Integer.parseInt(seat.substring(0, seat.length() - 1));
    }
}
