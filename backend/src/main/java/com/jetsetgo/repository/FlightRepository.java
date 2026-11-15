package com.jetsetgo.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import com.jetsetgo.domain.Flight;

public interface FlightRepository extends MongoRepository<Flight, String> {
    List<Flight> findByOriginCodeIgnoreCaseAndDestinationCodeIgnoreCase(String origin, String destination);

    @Query(value = "{ 'departure': { $gt: ?0 }, 'status': { $in: ?1 }, 'templateDisabled': { $ne: true } }",
            sort = "{ 'departure': 1 }")
    List<Flight> findUpcomingForBrowsing(Instant after, List<String> statuses, Pageable pageable);

    @Query(value = "{ 'dataSource': 'DEMO', 'status': 'SCHEDULED', 'departure': { $gt: ?0 }, "
            + "'templateDisabled': { $ne: true } }", sort = "{ 'departure': 1 }")
    List<Flight> findFeaturedDemoFlights(Instant after, Pageable pageable);
}
