package com.jetsetgo.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.jetsetgo.domain.Flight;

public interface FlightRepository extends MongoRepository<Flight, String> {
    List<Flight> findByOriginCodeIgnoreCaseAndDestinationCodeIgnoreCase(String origin, String destination);
}
