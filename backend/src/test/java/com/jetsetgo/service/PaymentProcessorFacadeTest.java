package com.jetsetgo.service;

import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorFacadeTest {
    @Mock RazorpayService razorpay;
    @Mock DemoPaymentService demoPayments;
    @InjectMocks PaymentProcessorFacade payments;

    @Test
    void routesRefundToTheProcessorThatRecordedThePayment() {
        Booking demo = booking(null);
        Booking razorpayBooking = booking("pay_test_123");

        payments.refund(demo);
        payments.refund(razorpayBooking);

        verify(demoPayments).refund("booking-id");
        verify(razorpay).refund("pay_test_123", new BigDecimal("1000"), "INR");
    }

    private static Booking booking(String paymentId) {
        return new Booking("booking-id", "JSG12345", "user", "flight", "1A",
                "Passenger", "p@example.com", new BigDecimal("1000"), "INR",
                BookingStatus.CONFIRMED, null, paymentId, Instant.now(), "key");
    }
}
