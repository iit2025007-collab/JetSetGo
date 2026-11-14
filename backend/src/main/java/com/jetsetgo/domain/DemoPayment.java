package com.jetsetgo.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("demo_payments")
@CompoundIndex(name = "user_demo_payment_idempotency_unique",
        def = "{'userId': 1, 'idempotencyKey': 1}", unique = true)
public record DemoPayment(
        @Id String id,
        String bookingId,
        String userId,
        String idempotencyKey,
        String method,
        BigDecimal amount,
        String currency,
        String status,
        String reference,
        Instant createdAt) {

    public DemoPayment refunded() {
        return new DemoPayment(id, bookingId, userId, idempotencyKey, method,
                amount, currency, "REFUNDED", reference, createdAt);
    }
}
