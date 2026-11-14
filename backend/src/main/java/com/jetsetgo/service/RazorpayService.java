package com.jetsetgo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
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

    public RazorpayService(@Value("${razorpay.key-id}") String keyId,
                           @Value("${razorpay.key-secret}") String keySecret,
                           @Value("${payment.mode:DEMO}") String paymentMode,
                           RestClient.Builder restClientBuilder,
                           BookingRepository bookings) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.paymentMode = paymentMode.toUpperCase();
        this.restClient = restClientBuilder.baseUrl("https://api.razorpay.com/v1").build();
        this.bookings = bookings;
    }

    public OrderResult createOrder(String userId, String bookingId) {
        Booking booking = ownedBooking(userId, bookingId);
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
        if (!isValidSignature(orderId, paymentId, signature, keySecret)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment signature is invalid");
        }
        try {
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
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to verify the payment status with Razorpay", exception);
        }
        return bookings.save(booking.confirmed(paymentId));
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

    private void requireCredentials() {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured. Add test credentials to the server environment.");
        }
    }

    public record OrderResult(String orderId, String keyId, long amount, String currency, boolean demoMode) {}
}
