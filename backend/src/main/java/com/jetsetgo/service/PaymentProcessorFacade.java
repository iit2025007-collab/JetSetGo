package com.jetsetgo.service;

import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Booking;

@Service
public class PaymentProcessorFacade implements PaymentProcessor {
    private final RazorpayService razorpay;
    private final DemoPaymentService demoPayments;

    public PaymentProcessorFacade(RazorpayService razorpay, DemoPaymentService demoPayments) {
        this.razorpay = razorpay;
        this.demoPayments = demoPayments;
    }

    @Override
    public OrderResult createOrder(String userId, String bookingId) {
        RazorpayService.OrderResult order = razorpay.createOrder(userId, bookingId);
        return new OrderResult(order.orderId(), order.keyId(), order.amount(), order.currency(), order.demoMode());
    }

    @Override
    public Booking verify(String userId, String bookingId, String orderId, String paymentId, String signature) {
        return razorpay.verify(userId, bookingId, orderId, paymentId, signature);
    }

    @Override
    public DemoResult completeDemo(String userId, String bookingId, String idempotencyKey,
                                   String method, boolean succeed) {
        DemoPaymentService.PaymentResult result = demoPayments.complete(userId, bookingId,
                idempotencyKey, method, succeed);
        return new DemoResult(result.booking(), result.reference(), result.status());
    }

    @Override
    public void refund(Booking booking) {
        if (booking.razorpayPaymentId() != null) {
            razorpay.refund(booking.razorpayPaymentId(), booking.amount(), booking.currency());
        } else {
            demoPayments.refund(booking.id());
        }
    }
}
