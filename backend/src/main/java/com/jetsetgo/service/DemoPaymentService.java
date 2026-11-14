package com.jetsetgo.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.domain.DemoPayment;
import com.jetsetgo.repository.BookingRepository;
import com.jetsetgo.repository.DemoPaymentRepository;

@Service
public class DemoPaymentService {
    private final BookingRepository bookings;
    private final DemoPaymentRepository payments;
    private final MongoTemplate mongo;
    private final boolean demoMode;

    public DemoPaymentService(BookingRepository bookings, DemoPaymentRepository payments,
                              MongoTemplate mongo, @Value("${payment.mode:DEMO}") String paymentMode) {
        this.bookings = bookings;
        this.payments = payments;
        this.mongo = mongo;
        this.demoMode = "DEMO".equalsIgnoreCase(paymentMode);
    }

    public PaymentResult complete(String userId, String bookingId, String idempotencyKey,
                                  String method, boolean succeed) {
        requireDemoMode();
        DemoPayment previous = payments.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
        if (previous != null) {
            if (!previous.bookingId().equals(bookingId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment retry key was already used");
            }
            Booking existingBooking = ownedBooking(userId, bookingId);
            if ("SUCCEEDED".equals(previous.status())
                    && existingBooking.status() == BookingStatus.PENDING_PAYMENT) {
                existingBooking = confirmDemoBooking(existingBooking);
            } else if ("SUCCEEDED".equals(previous.status())
                    && existingBooking.status() != BookingStatus.CONFIRMED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking payment is still processing");
            }
            return new PaymentResult(existingBooking, previous.reference(), previous.status());
        }

        Booking booking = ownedBooking(userId, bookingId);
        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            if (booking.status() == BookingStatus.CONFIRMED && !succeed) {
                return new PaymentResult(booking, "already-confirmed", "SUCCEEDED");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is not awaiting demo payment");
        }

        Booking processing = null;
        if (succeed) {
            processing = mongo.findAndModify(
                    Query.query(Criteria.where("_id").is(booking.id())
                            .and("status").is(BookingStatus.PENDING_PAYMENT)),
                    Update.update("status", BookingStatus.PAYMENT_PROCESSING),
                    FindAndModifyOptions.options().returnNew(true), Booking.class);
            if (processing == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking payment is already being processed");
            }
        }

        String status = succeed ? "SUCCEEDED" : "FAILED";
        String reference = "JSG-DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DemoPayment payment = new DemoPayment(null, booking.id(), userId, idempotencyKey, method,
                booking.amount(), booking.currency(), status, reference, Instant.now());
        try {
            payments.save(payment);
            Booking result = succeed ? bookings.save(processing.confirmed(null)) : booking;
            return new PaymentResult(result, reference, status);
        } catch (RuntimeException failure) {
            if (processing != null) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.id())
                                .and("status").is(BookingStatus.PAYMENT_PROCESSING)),
                        Update.update("status", BookingStatus.PENDING_PAYMENT), Booking.class);
            }
            throw failure;
        }
    }

    public void refund(String bookingId) {
        requireDemoMode();
        DemoPayment payment = payments.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                        bookingId, "SUCCEEDED")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "The demo payment record was not found"));
        payments.save(payment.refunded());
    }

    private Booking confirmDemoBooking(Booking booking) {
        Booking processing = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(booking.id())
                        .and("status").is(BookingStatus.PENDING_PAYMENT)),
                Update.update("status", BookingStatus.PAYMENT_PROCESSING),
                FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (processing == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking payment is already being processed");
        }
        return bookings.save(processing.confirmed(null));
    }

    private Booking ownedBooking(String userId, String bookingId) {
        return bookings.findById(bookingId)
                .filter(booking -> booking.userId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void requireDemoMode() {
        if (!demoMode) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Demo payments are disabled; use Razorpay Test Mode.");
        }
    }

    public record PaymentResult(Booking booking, String reference, String status) {}
}
