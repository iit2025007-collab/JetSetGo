package com.jetsetgo.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.service.RazorpayService;
import com.jetsetgo.web.dto.BookingResponse;

@RestController
@RequestMapping("/api/bookings/{bookingId}/payments")
public class PaymentController {
    private final RazorpayService razorpay;

    public PaymentController(RazorpayService razorpay) {
        this.razorpay = razorpay;
    }

    @PostMapping("/order")
    public RazorpayService.OrderResult createOrder(Authentication authentication, @PathVariable String bookingId) {
        return razorpay.createOrder(authentication.getName(), bookingId);
    }

    @PostMapping("/verify")
    public BookingResponse verify(Authentication authentication, @PathVariable String bookingId,
                                  @Valid @RequestBody VerifyPaymentRequest request) {
        return BookingResponse.from(razorpay.verify(authentication.getName(), bookingId,
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature()));
    }

    public record VerifyPaymentRequest(@NotBlank String razorpayOrderId,
                                      @NotBlank String razorpayPaymentId,
                                      @NotBlank String razorpaySignature) {}
}
