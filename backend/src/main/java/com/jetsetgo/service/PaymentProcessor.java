package com.jetsetgo.service;

import java.math.BigDecimal;

import com.jetsetgo.domain.Booking;

public interface PaymentProcessor {
    OrderResult createOrder(String userId, String bookingId);

    Booking verify(String userId, String bookingId, String orderId, String paymentId, String signature);

    DemoResult completeDemo(String userId, String bookingId, String idempotencyKey,
                            String method, boolean succeed);

    void refund(Booking booking);

    record OrderResult(String orderId, String keyId, long amount, String currency, boolean demoMode) {}
    record DemoResult(Booking booking, String reference, String status) {}
}
