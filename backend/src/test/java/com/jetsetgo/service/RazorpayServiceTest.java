package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.repository.BookingRepository;

@ExtendWith(MockitoExtension.class)
class RazorpayServiceTest {
    @Mock BookingRepository bookings;
    @Mock MongoTemplate mongo;
    @Mock RestClient.Builder restClientBuilder;
    @Mock RestClient restClient;

    @Test
    void acceptsOnlySignatureForMatchingOrderAndPayment() throws Exception {
        String secret = "test-secret";
        String orderId = "order_123";
        String paymentId = "pay_456";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(
                mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));

        assertTrue(RazorpayService.isValidSignature(orderId, paymentId, signature, secret));
        assertFalse(RazorpayService.isValidSignature(orderId, "pay_changed", signature, secret));
        assertFalse(RazorpayService.isValidSignature(orderId, paymentId, "not-a-signature", secret));
        assertFalse(RazorpayService.isValidSignature(orderId, paymentId, null, secret));
    }

    @Test
    void doesNotCallProviderWhenExpiryAlreadyWonThePaymentTransition() {
        Booking pending = new Booking("booking", "JSG12345", "user", "flight", "2A",
                "A Passenger", "passenger@example.com", new BigDecimal("5000"), "INR",
                BookingStatus.PENDING_PAYMENT, "order_123", null, Instant.now(), "create-key",
                null, Instant.now().plusSeconds(300));
        when(bookings.findById("booking")).thenReturn(Optional.of(pending));
        when(restClientBuilder.baseUrl("https://api.razorpay.com/v1")).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Booking.class))).thenReturn(null);
        RazorpayService service = new RazorpayService("test-key", "test-secret", "RAZORPAY_TEST",
                restClientBuilder, bookings, mongo);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.verify("user", "booking", "order_123", "pay_456", "signature"));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(restClient, never()).get();
    }
}
