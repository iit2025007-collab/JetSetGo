package com.jetsetgo.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.repository.BookingRepository;

@Service
public class BookingExpiryService {
    private final BookingRepository bookings;
    private final MongoTemplate mongo;
    private final SeatInventoryService seats;

    public BookingExpiryService(BookingRepository bookings, MongoTemplate mongo, SeatInventoryService seats) {
        this.bookings = bookings;
        this.mongo = mongo;
        this.seats = seats;
    }

    @Scheduled(fixedDelayString = "${app.booking-expiry-scan-ms:60000}")
    public void releaseExpiredReservations() {
        Instant now = Instant.now();
        List<Booking> expired = bookings.findByStatusAndExpiresAtBefore(BookingStatus.PENDING_PAYMENT, now);
        for (Booking booking : expired) {
            Booking transitioned = mongo.findAndModify(
                    Query.query(Criteria.where("_id").is(booking.id())
                            .and("status").is(BookingStatus.PENDING_PAYMENT)
                            .and("expiresAt").lt(now)),
                    Update.update("status", BookingStatus.EXPIRED),
                    FindAndModifyOptions.options().returnNew(true), Booking.class);
            if (transitioned == null) {
                continue;
            }
            List<String> outbound = booking.tripDetails() == null
                    || booking.tripDetails().seatNumbers().isEmpty()
                    ? List.of(booking.seatNumber()) : booking.tripDetails().seatNumbers();
            outbound.forEach(seat -> seats.release(booking.flightId(), seat, booking.id()));
            if (booking.tripDetails() != null && booking.tripDetails().returnFlightId() != null) {
                booking.tripDetails().returnSeatNumbers().forEach(seat ->
                        seats.release(booking.tripDetails().returnFlightId(), seat, booking.id()));
            }
        }
    }
}
