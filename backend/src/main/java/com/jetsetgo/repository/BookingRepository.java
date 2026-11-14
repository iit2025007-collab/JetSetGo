package com.jetsetgo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.jetsetgo.domain.Booking;

public interface BookingRepository extends MongoRepository<Booking, String> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Booking> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    boolean existsByFlightId(String flightId);
}
