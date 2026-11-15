package com.jetsetgo.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

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
        String idempotencyKey,
        TripDetails tripDetails) {

    public Booking(String id, String pnr, String userId, String flightId, String seatNumber,
                   String passengerName, String passengerEmail, BigDecimal amount, String currency,
                   BookingStatus status, String razorpayOrderId, String razorpayPaymentId,
                   Instant createdAt, String idempotencyKey) {
        this(id, pnr, userId, flightId, seatNumber, passengerName, passengerEmail, amount,
                currency, status, razorpayOrderId, razorpayPaymentId, createdAt, idempotencyKey, null);
    }

    public Booking withPaymentOrder(String orderId) {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, status, orderId, razorpayPaymentId,
                createdAt, idempotencyKey, tripDetails);
    }

    public Booking confirmed(String paymentId) {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, BookingStatus.CONFIRMED, razorpayOrderId,
                paymentId, createdAt, idempotencyKey, tripDetails);
    }

    public Booking cancelled() {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, BookingStatus.CANCELLED, razorpayOrderId,
                razorpayPaymentId, createdAt, idempotencyKey, tripDetails);
    }

    public record TripDetails(String returnFlightId, List<String> seatNumbers,
                              List<String> returnSeatNumbers, String travelClass,
                              List<Passenger> passengers) {
        public TripDetails {
            seatNumbers = seatNumbers == null ? List.of() : List.copyOf(seatNumbers);
            returnSeatNumbers = returnSeatNumbers == null ? List.of() : List.copyOf(returnSeatNumbers);
            passengers = passengers == null ? List.of() : List.copyOf(passengers);
        }
    }

    public record Passenger(String name, String nationality, LocalDate passportExpiryDate) {
    }
}
