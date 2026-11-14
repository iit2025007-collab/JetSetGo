package com.jetsetgo.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.jetsetgo.domain.DemoPayment;

public interface DemoPaymentRepository extends MongoRepository<DemoPayment, String> {
    Optional<DemoPayment> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    Optional<DemoPayment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(String bookingId, String status);
}
