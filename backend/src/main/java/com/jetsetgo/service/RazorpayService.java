package com.jetsetgo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RazorpayService {
    private final String keyId;
    private final String keySecret;
    private final String paymentMode;
    private final RestClient restClient;
    private final BookingRepository bookings;
    private final MongoTemplate mongo;

    public RazorpayService(@Value("${razorpay.key-id}") String keyId,
                           @Value("${razorpay.key-secret}") String keySecret,
                           @Value("${payment.mode:DEMO}") String paymentMode,
                           RestClient.Builder restClientBuilder,
                           BookingRepository bookings,
                           MongoTemplate mongo) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.paymentMode = paymentMode.toUpperCase();
        this.restClient = restClientBuilder.baseUrl("https://api.razorpay.com/v1").build();
        this.bookings = bookings;
        this.mongo = mongo;
    }

    public OrderResult createOrder(String userId, String bookingId) {
        Booking booking = ownedBooking(userId, bookingId);
        ensureNotExpired(booking);
        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is not awaiting payment");
        }
        boolean demoMode = "DEMO".equals(paymentMode);
        if (booking.razorpayOrderId() != null) {
            if (booking.razorpayOrderId().startsWith("demo_") != demoMode) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Payment mode changed after this booking was created");
            }
            return new OrderResult(booking.razorpayOrderId(), keyId,
                    booking.amount().movePointRight(2).longValueExact(), booking.currency(), demoMode);
        }
        if (demoMode) {
            Booking updated = bookings.save(booking.withPaymentOrder("demo_" + booking.id()));
            return new OrderResult(updated.razorpayOrderId(), "", updated.amount().movePointRight(2).longValueExact(),
                    updated.currency(), true);
        }
        if (!"RAZORPAY_TEST".equals(paymentMode)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unknown payment mode configuration");
        }
        requireCredentials();
        try {
            JsonNode response = restClient.post().uri("/orders")
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .body(Map.of(
                            "amount", booking.amount().movePointRight(2).longValueExact(),
                            "currency", booking.currency(),
                            "receipt", booking.pnr(),
                            "notes", Map.of("bookingId", booking.id())))
                    .retrieve().body(JsonNode.class);
            if (response == null || response.path("id").asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay returned an invalid order response");
            }
            Booking updated = bookings.save(booking.withPaymentOrder(response.path("id").asText()));
            return new OrderResult(updated.razorpayOrderId(), keyId,
                    updated.amount().movePointRight(2).longValueExact(), updated.currency(), false);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create a Razorpay order", exception);
        }
    }

    public Booking verify(String userId, String bookingId, String orderId, String paymentId, String signature) {
        Booking booking = ownedBooking(userId, bookingId);
        ensureNotExpired(booking);
        if (!"RAZORPAY_TEST".equals(paymentMode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Razorpay signature verification is available only in Razorpay Test Mode");
        }
        if (booking.status() == BookingStatus.CONFIRMED && paymentId.equals(booking.razorpayPaymentId())) {
            return booking;
        }
        if (booking.status() != BookingStatus.PENDING_PAYMENT
                || booking.razorpayOrderId() == null
                || !booking.razorpayOrderId().equals(orderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment does not match this pending booking");
        }
        requireCredentials();
        Booking processing = beginVerification(booking, orderId);
        try {
            if (!isValidSignature(orderId, paymentId, signature, keySecret)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment signature is invalid");
            }
            JsonNode payment = restClient.get().uri("/payments/{paymentId}", paymentId)
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .retrieve().body(JsonNode.class);
            if (payment == null
                    || !orderId.equals(payment.path("order_id").asText())
                    || !"captured".equals(payment.path("status").asText())
                    || payment.path("amount").asLong(-1) != booking.amount().movePointRight(2).longValueExact()
                    || !booking.currency().equals(payment.path("currency").asText())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Razorpay has not confirmed the full payment for this booking");
            }
            return bookings.save(processing.confirmed(paymentId));
        } catch (RuntimeException failure) {
            restorePending(processing.id(), failure);
            if (failure instanceof ResponseStatusException) {
                throw failure;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to verify the payment status with Razorpay", failure);
        }
    }

    private Booking beginVerification(Booking booking, String orderId) {
        Instant now = Instant.now();
        Criteria unexpired = new Criteria().orOperator(
                Criteria.where("expiresAt").exists(false),
                Criteria.where("expiresAt").is(null),
                Criteria.where("expiresAt").gt(now));
        Booking processing = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(booking.id()),
                        Criteria.where("status").is(BookingStatus.PENDING_PAYMENT),
                        Criteria.where("razorpayOrderId").is(orderId),
                        unexpired)),
                Update.update("status", BookingStatus.PAYMENT_PROCESSING),
                FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (processing == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This booking payment is expired or already being processed");
        }
        return processing;
    }

    private void restorePending(String bookingId, RuntimeException failure) {
        try {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(bookingId)
                            .and("status").is(BookingStatus.PAYMENT_PROCESSING)),
                    Update.update("status", BookingStatus.PENDING_PAYMENT), Booking.class);
        } catch (RuntimeException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    public void refund(String paymentId, java.math.BigDecimal amount, String currency) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The confirmed booking has no payment reference");
        }
        requireCredentials();
        try {
            restClient.post().uri("/payments/{paymentId}/refund", paymentId)
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .body(Map.of("amount", amount.movePointRight(2).longValueExact(),
                            "notes", Map.of("reason", "Customer booking cancellation")))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Razorpay refund could not be completed; the booking was not cancelled", exception);
        }
    }

    public static boolean isValidSignature(String orderId, String paymentId, String signature, String secret) {
        if (orderId == null || paymentId == null || signature == null || secret == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(
                    mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)))
                    .getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private Booking ownedBooking(String userId, String bookingId) {
        return bookings.findById(bookingId)
                .filter(booking -> booking.userId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private static void ensureNotExpired(Booking booking) {
        if (booking.expiresAt() != null && !booking.expiresAt().isAfter(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This payment reservation has expired. Please create a new booking.");
        }
    }

    private void requireCredentials() {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured. Add test credentials to the server environment.");
        }
        if (!keyId.startsWith("rzp_test_")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay Test Mode requires a test key ID.");
        }
    }

    public record OrderResult(String orderId, String keyId, long amount, String currency, boolean demoMode) {}
}
