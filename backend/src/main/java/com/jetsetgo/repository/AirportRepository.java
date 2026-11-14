package com.jetsetgo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.jetsetgo.domain.Airport;

public interface AirportRepository extends MongoRepository<Airport, String> {
}
