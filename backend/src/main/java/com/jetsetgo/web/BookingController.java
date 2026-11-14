package com.jetsetgo.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.service.BookingService;
import com.jetsetgo.web.dto.BookingResponse;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    public BookingResponse create(Authentication authentication,
                                   @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) String key,
                                   @Valid @RequestBody BookingRequest request) {
        return BookingResponse.from(bookings.create(authentication.getName(), key,
                new BookingService.CreateBooking(request.flightId(), request.seatNumber(),
                        request.passengerName(), request.passengerEmail())));
    }

    @GetMapping
    public List<BookingResponse> list(Authentication authentication) {
        return bookings.forUser(authentication.getName()).stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BookingResponse get(Authentication authentication, @PathVariable String id) {
        return BookingResponse.from(bookings.getForUser(authentication.getName(), id));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(Authentication authentication, @PathVariable String id) {
        return BookingResponse.from(bookings.cancel(authentication.getName(), id));
    }

    public record BookingRequest(@NotBlank String flightId,
                                 @NotBlank @Pattern(regexp = "[1-9][A-F]") String seatNumber,
                                 @NotBlank @Size(max = 100) String passengerName,
                                 @NotBlank @Email @Size(max = 254) String passengerEmail) {}
}
