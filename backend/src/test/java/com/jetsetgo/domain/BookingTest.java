package com.jetsetgo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class BookingTest {
    @Test
    void paymentAndCancellationTransitionsPreserveBookingDetails() {
        Booking pending = new Booking("id", "PNR12345", "user", "flight", "2A",
                "A. Passenger", "passenger@example.com", new BigDecimal("6499"), "INR",
                BookingStatus.PENDING_PAYMENT, "order_1", null, Instant.EPOCH, "key");

        Booking confirmed = pending.confirmed("pay_1");
        Booking cancelled = confirmed.cancelled();

        assertEquals(BookingStatus.CONFIRMED, confirmed.status());
        assertEquals("pay_1", confirmed.razorpayPaymentId());
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(pending.pnr(), cancelled.pnr());
        assertEquals(pending.amount(), cancelled.amount());
        assertEquals(pending.seatNumber(), cancelled.seatNumber());
    }
}
