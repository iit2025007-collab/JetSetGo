package com.jetsetgo.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.repository.BookingRepository;

@ExtendWith(MockitoExtension.class)
class BookingExpiryServiceTest {
    @Mock BookingRepository bookings;
    @Mock MongoTemplate mongo;
    @Mock SeatInventoryService seats;
    @InjectMocks BookingExpiryService service;

    @Test
    void releasesEveryLegOnlyAfterPendingBookingIsAtomicallyExpired() {
        Booking.TripDetails trip = new Booking.TripDetails("return-flight", List.of("10A"),
                List.of("24F"), "ECONOMY", List.of());
        Booking booking = new Booking("booking-id", "JSG12345", "user", "outbound-flight",
                "10A", "Passenger", "p@example.com", new BigDecimal("1000"), "INR",
                BookingStatus.PENDING_PAYMENT, null, null, Instant.now().minusSeconds(1200),
                "request-key", trip, Instant.now().minusSeconds(300));
        when(bookings.findByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(booking));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Booking.class))).thenReturn(booking.expired());

        service.releaseExpiredReservations();

        verify(seats).release("outbound-flight", "10A", "booking-id");
        verify(seats).release("return-flight", "24F", "booking-id");
    }

    @Test
    void doesNotReleaseSeatsWhenPaymentWonTheStateTransitionRace() {
        Booking booking = new Booking("booking-id", "JSG12345", "user", "outbound-flight",
                "1A", "Passenger", "p@example.com", new BigDecimal("1000"), "INR",
                BookingStatus.PENDING_PAYMENT, null, null, Instant.now().minusSeconds(1200),
                "request-key", null, Instant.now().minusSeconds(300));
        when(bookings.findByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(booking));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Booking.class))).thenReturn(null);

        service.releaseExpiredReservations();

        org.mockito.Mockito.verifyNoInteractions(seats);
    }
}
