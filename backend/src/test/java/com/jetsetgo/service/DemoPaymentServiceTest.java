package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.domain.DemoPayment;
import com.jetsetgo.repository.BookingRepository;
import com.jetsetgo.repository.DemoPaymentRepository;

@ExtendWith(MockitoExtension.class)
class DemoPaymentServiceTest {
    @Mock BookingRepository bookings;
    @Mock DemoPaymentRepository payments;
    @Mock MongoTemplate mongo;
    DemoPaymentService service;

    @BeforeEach
    void setUp() {
        service = new DemoPaymentService(bookings, payments, mongo, "DEMO");
    }

    @Test
    void successfulDemoPaymentConfirmsBookingWithoutPaymentCredentials() {
        Booking pending = booking(BookingStatus.PENDING_PAYMENT);
        Booking processing = booking(BookingStatus.PAYMENT_PROCESSING);
        Booking confirmed = booking(BookingStatus.CONFIRMED);
        when(payments.findByUserIdAndIdempotencyKey("user", "attempt-123")).thenReturn(Optional.empty());
        when(bookings.findById("booking")).thenReturn(Optional.of(pending));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Booking.class))).thenReturn(processing);
        when(payments.save(any(DemoPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookings.save(any(Booking.class))).thenReturn(confirmed);

        DemoPaymentService.PaymentResult result =
                service.complete("user", "booking", "attempt-123", "UPI", true);

        assertEquals("SUCCEEDED", result.status());
        assertEquals(BookingStatus.CONFIRMED, result.booking().status());
        verify(bookings).save(any(Booking.class));
    }

    @Test
    void failedDemoPaymentLeavesBookingPending() {
        Booking pending = booking(BookingStatus.PENDING_PAYMENT);
        when(payments.findByUserIdAndIdempotencyKey("user", "attempt-456")).thenReturn(Optional.empty());
        when(bookings.findById("booking")).thenReturn(Optional.of(pending));
        when(payments.save(any(DemoPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DemoPaymentService.PaymentResult result =
                service.complete("user", "booking", "attempt-456", "CARD", false);

        assertEquals("FAILED", result.status());
        assertEquals(BookingStatus.PENDING_PAYMENT, result.booking().status());
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Booking.class));
        verify(bookings, never()).save(any(Booking.class));
    }

    private static Booking booking(BookingStatus status) {
        return new Booking("booking", "JSG12345", "user", "flight", "2A",
                "A Passenger", "passenger@example.com", new BigDecimal("5000"), "INR",
                status, "demo_booking", null, Instant.EPOCH, "create-key");
    }
}
