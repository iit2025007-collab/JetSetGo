package com.jetsetgo.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.jetsetgo.domain.SeatInventory;

public interface SeatInventoryRepository extends MongoRepository<SeatInventory, String> {
    List<SeatInventory> findByFlightIdAndAvailableTrueOrderBySeatNumber(String flightId);
}
