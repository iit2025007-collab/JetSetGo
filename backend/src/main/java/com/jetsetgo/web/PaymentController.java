package com.jetsetgo.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.service.RazorpayService;
import com.jetsetgo.service.DemoPaymentService;
import com.jetsetgo.web.dto.BookingResponse;

@RestController
@RequestMapping("/api/bookings/{bookingId}/payments")
public class PaymentController {
    private final RazorpayService razorpay;
    private final DemoPaymentService demoPayments;

    public PaymentController(RazorpayService razorpay, DemoPaymentService demoPayments) {
        this.razorpay = razorpay;
        this.demoPayments = demoPayments;
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

    @PostMapping("/demo/complete")
    public DemoPaymentResponse completeDemo(Authentication authentication, @PathVariable String bookingId,
                                             @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) String key,
                                             @Valid @RequestBody DemoPaymentRequest request) {
        DemoPaymentService.PaymentResult result = demoPayments.complete(authentication.getName(), bookingId,
                key, request.method(), request.succeed());
        return new DemoPaymentResponse(BookingResponse.from(result.booking()), result.reference(), result.status());
    }

    public record VerifyPaymentRequest(@NotBlank String razorpayOrderId,
                                      @NotBlank String razorpayPaymentId,
                                      @NotBlank String razorpaySignature) {}
    public record DemoPaymentRequest(@NotBlank @Pattern(regexp = "UPI|CARD|NET_BANKING|WALLET") String method,
                                     boolean succeed) {}
    public record DemoPaymentResponse(BookingResponse booking, String reference, String status) {}
}
