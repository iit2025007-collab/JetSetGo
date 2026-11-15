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
@CompoundIndex(name = "booking_expiry_idx", def = "{'status': 1, 'expiresAt': 1}")
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
        TripDetails tripDetails,
        Instant expiresAt) {

    public Booking(String id, String pnr, String userId, String flightId, String seatNumber,
                   String passengerName, String passengerEmail, BigDecimal amount, String currency,
                   BookingStatus status, String razorpayOrderId, String razorpayPaymentId,
                   Instant createdAt, String idempotencyKey) {
        this(id, pnr, userId, flightId, seatNumber, passengerName, passengerEmail, amount,
                currency, status, razorpayOrderId, razorpayPaymentId, createdAt, idempotencyKey, null, null);
    }

    public Booking(String id, String pnr, String userId, String flightId, String seatNumber,
                   String passengerName, String passengerEmail, BigDecimal amount, String currency,
                   BookingStatus status, String razorpayOrderId, String razorpayPaymentId,
                   Instant createdAt, String idempotencyKey, TripDetails tripDetails) {
        this(id, pnr, userId, flightId, seatNumber, passengerName, passengerEmail, amount,
                currency, status, razorpayOrderId, razorpayPaymentId, createdAt, idempotencyKey,
                tripDetails, null);
    }

    public Booking withPaymentOrder(String orderId) {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, status, orderId, razorpayPaymentId,
                createdAt, idempotencyKey, tripDetails, expiresAt);
    }

    public Booking confirmed(String paymentId) {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, BookingStatus.CONFIRMED, razorpayOrderId,
                paymentId, createdAt, idempotencyKey, tripDetails, null);
    }

    public Booking cancelled() {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, BookingStatus.CANCELLED, razorpayOrderId,
                razorpayPaymentId, createdAt, idempotencyKey, tripDetails, expiresAt);
    }

    public Booking expired() {
        return new Booking(id, pnr, userId, flightId, seatNumber, passengerName,
                passengerEmail, amount, currency, BookingStatus.EXPIRED, razorpayOrderId,
                razorpayPaymentId, createdAt, idempotencyKey, tripDetails, expiresAt);
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
