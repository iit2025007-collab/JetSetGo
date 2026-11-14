package com.jetsetgo.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("bookings")
@CompoundIndex(name = "user_idempotency_unique", def = "{'userId': 1, 'idempotencyKey': 1}", unique = true)
public record Booking(
        @Id String id,
        String pnr,
        String userId,
        String flightId,
        String seatNumber,
        String passengerName,
        String passengerEmail,
        BigDecimal amount,
        String currency,
        BookingStatus status,
        String razorpayOrderId,
        String razorpayPaymentId,
        Instant createdAt,
        String idempotencyKey) {

    public Booking withPaymentOrder(String orderId) {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, status, orderId, razorpayPaymentId,
                createdAt, idempotencyKey);
    }

    public Booking confirmed(String paymentId) {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, BookingStatus.CONFIRMED, razorpayOrderId,
                paymentId, createdAt, idempotencyKey);
    }

    public Booking cancelled() {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, BookingStatus.CANCELLED, razorpayOrderId,
                razorpayPaymentId, createdAt, idempotencyKey);
    }
}
