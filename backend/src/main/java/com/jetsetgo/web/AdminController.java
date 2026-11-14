package com.jetsetgo.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.service.AdminService;
import com.jetsetgo.web.dto.BookingResponse;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/summary")
    public AdminService.Summary summary() {
        return admin.summary();
    }

    @GetMapping("/flights")
    public List<Flight> flights() {
        return admin.flights();
    }

    @PostMapping("/flights")
    public Flight addFlight(@Valid @RequestBody FlightRequest request) {
        return admin.addFlight(new AdminService.NewFlight(request.airline(), request.flightNumber(),
                request.originCode(), request.originCity(), request.destinationCode(),
                request.destinationCity(), request.departure(), request.arrival(),
                request.price(), request.capacity()));
    }

    @PatchMapping("/flights/{id}/status")
    public Flight changeStatus(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return admin.changeStatus(id, request.status());
    }

    @DeleteMapping("/flights/{id}")
    public void deleteFlight(@PathVariable String id) {
        admin.deleteFlight(id);
    }

    @GetMapping("/bookings")
    public List<BookingResponse> bookings() {
        return admin.bookings().stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/users")
    public List<AdminService.UserSummary> users() {
        return admin.users();
    }

    public record FlightRequest(@NotBlank @Size(max = 100) String airline,
                                @NotBlank @Size(max = 12) String flightNumber,
                                @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String originCode,
                                @NotBlank @Size(max = 100) String originCity,
                                @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String destinationCode,
                                @NotBlank @Size(max = 100) String destinationCity,
                                @NotNull Instant departure, @NotNull Instant arrival,
                                @NotNull @DecimalMin("1.00") BigDecimal price,
                                @Min(6) @Max(360) int capacity) {}
    public record StatusRequest(@NotBlank String status) {}
}
