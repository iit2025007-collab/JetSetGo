package com.jetsetgo.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.domain.Booking.TripDetails;

public record BookingResponse(String id, String pnr, String flightId, String seatNumber,
                              String passengerName, String passengerEmail, BigDecimal amount,
                              String currency, BookingStatus status, Instant createdAt,
                              TripDetails tripDetails) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.id(), booking.pnr(), booking.flightId(),
                booking.seatNumber(), booking.passengerName(), booking.passengerEmail(),
                booking.amount(), booking.currency(), booking.status(), booking.createdAt(),
                booking.tripDetails());
    }
}
