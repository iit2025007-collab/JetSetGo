package com.jetsetgo.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.BookingRepository;
import com.jetsetgo.repository.FlightRepository;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final FlightRepository flights;
    private final MongoTemplate mongo;
    private final RazorpayService razorpay;

    public BookingService(BookingRepository bookings, FlightRepository flights,
                         MongoTemplate mongo, RazorpayService razorpay) {
        this.bookings = bookings;
        this.flights = flights;
        this.mongo = mongo;
        this.razorpay = razorpay;
    }

    public Booking create(String userId, String idempotencyKey, CreateBooking request) {
        return bookings.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseGet(() -> reserveAndCreate(userId, idempotencyKey, request));
    }

    private Booking reserveAndCreate(String userId, String idempotencyKey, CreateBooking request) {
        Flight flight = flights.findById(request.flightId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        if (!"SCHEDULED".equals(flight.status()) || !flight.departure().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This flight is not available for booking");
        }

        String seatId = SeatInventory.idFor(flight.id(), request.seatNumber());
        SeatInventory reserved = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(seatId).and("available").is(true)),
                Update.update("available", false),
                FindAndModifyOptions.options().returnNew(true),
                SeatInventory.class);
        if (reserved == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That seat is no longer available");
        }

        Booking booking = new Booking(null, UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                userId, flight.id(), request.seatNumber(), request.passengerName().trim(),
                request.passengerEmail().trim().toLowerCase(), flight.price(), flight.currency(),
                BookingStatus.PENDING_PAYMENT, null, null, Instant.now(), idempotencyKey);
        try {
            return bookings.save(booking);
        } catch (DuplicateKeyException duplicate) {
            releaseSeat(seatId);
            return bookings.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> duplicate);
        } catch (RuntimeException failure) {
            releaseSeat(seatId);
            throw failure;
        }
    }

    public List<Booking> forUser(String userId) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Booking getForUser(String userId, String bookingId) {
        return bookings.findById(bookingId)
                .filter(booking -> booking.userId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    public Booking cancel(String userId, String bookingId) {
        Booking booking = getForUser(userId, bookingId);
        if (booking.status() == BookingStatus.CANCELLED) return booking;
        Flight flight = flights.findById(booking.flightId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        if (!flight.departure().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A departed flight cannot be cancelled");
        }
        BookingStatus originalStatus = booking.status();
        if (originalStatus != BookingStatus.CONFIRMED && originalStatus != BookingStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is already being cancelled");
        }
        Booking cancelling = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(booking.id()).and("status").is(originalStatus)),
                Update.update("status", BookingStatus.CANCELLING),
                FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (cancelling == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has already changed");
        }
        if (originalStatus == BookingStatus.CONFIRMED) {
            try {
                razorpay.refund(booking.razorpayPaymentId(), booking.amount(), booking.currency());
            } catch (RuntimeException failure) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.id())
                                .and("status").is(BookingStatus.CANCELLING)),
                        Update.update("status", originalStatus), Booking.class);
                throw failure;
            }
        }
        Booking cancelled = bookings.save(cancelling.cancelled());
        releaseSeat(SeatInventory.idFor(booking.flightId(), booking.seatNumber()));
        return cancelled;
    }

    private void releaseSeat(String seatId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(seatId)),
                Update.update("available", true), SeatInventory.class);
    }

    public record CreateBooking(String flightId, String seatNumber,
                                String passengerName, String passengerEmail) {}
}
