package com.jetsetgo.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;
import com.jetsetgo.service.AdminService;
import com.jetsetgo.service.RouteTemplateService;
import com.jetsetgo.web.dto.BookingResponse;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final RouteTemplateService routeTemplates;

    public AdminController(AdminService admin, RouteTemplateService routeTemplates) {
        this.admin = admin;
        this.routeTemplates = routeTemplates;
    }

    @GetMapping("/summary")
    public AdminService.Summary summary() {
        return admin.summary();
    }

    @GetMapping("/flights")
    public List<Flight> flights(@RequestParam(required = false) String from,
                                @RequestParam(required = false) String to,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) String flightNumber) {
        return admin.flights(from, to, date, flightNumber);
    }

    @GetMapping("/routes")
    public List<RouteTemplate> routeTemplates() {
        return routeTemplates.list();
    }

    @PostMapping("/routes")
    public RouteTemplate createRoute(@Valid @RequestBody RouteTemplateRequest request) {
        return routeTemplates.create(request.toInput());
    }

    @PutMapping("/routes/{id}")
    public RouteTemplate updateRoute(@PathVariable String id,
                                     @Valid @RequestBody RouteTemplateRequest request) {
        return routeTemplates.update(id, request.toInput());
    }

    @PatchMapping("/routes/{id}/active")
    public RouteTemplate setRouteActive(@PathVariable String id,
                                        @Valid @RequestBody ActiveRequest request) {
        return routeTemplates.setActive(id, request.active());
    }

    @PostMapping("/flights")
    public Flight addFlight(@Valid @RequestBody FlightRequest request) {
        return admin.addFlight(new AdminService.NewFlight(request.airline(), request.flightNumber(),
                request.originCode(), request.originCity(), request.destinationCode(),
                request.destinationCity(), request.departure(), request.arrival(),
                request.price(), request.capacity(), request.originTimeZone(), request.destinationTimeZone(),
                request.aircraft(), request.originTerminal(), request.destinationTerminal()));
    }

    @PutMapping("/flights/{id}")
    public Flight updateFlight(@PathVariable String id, @Valid @RequestBody FlightRequest request) {
        return admin.updateFlight(id, new AdminService.NewFlight(request.airline(), request.flightNumber(),
                request.originCode(), request.originCity(), request.destinationCode(),
                request.destinationCity(), request.departure(), request.arrival(),
                request.price(), request.capacity(), request.originTimeZone(), request.destinationTimeZone(),
                request.aircraft(), request.originTerminal(), request.destinationTerminal()));
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
    public List<BookingResponse> bookings(@RequestParam(required = false) String status) {
        return admin.bookings(status).stream().map(BookingResponse::from).toList();
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable String id) {
        return BookingResponse.from(admin.cancelBooking(id));
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
                                @Min(6) @Max(360) int capacity,
                                String originTimeZone, String destinationTimeZone,
                                @Size(max = 100) String aircraft,
                                @Size(max = 40) String originTerminal,
                                @Size(max = 40) String destinationTerminal) {}
    public record StatusRequest(@NotBlank String status) {}
    public record ActiveRequest(boolean active) {}
    public record RouteTemplateRequest(@NotBlank @Size(max = 100) String airline,
                                       @NotBlank @Size(max = 12) String flightNumber,
                                       @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String originCode,
                                       @NotBlank @Size(max = 100) String originCity,
                                       @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String destinationCode,
                                       @NotBlank @Size(max = 100) String destinationCity,
                                       @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String departureLocalTime,
                                       @Min(20) @Max(1800) int durationMinutes,
                                       @Size(min = 1, max = 7) List<@Min(1) @Max(7) Integer> operatingDays,
                                       @NotBlank String originTimeZone,
                                       @NotBlank String destinationTimeZone,
                                       @NotNull @DecimalMin("1.00") BigDecimal baseFare,
                                       @Pattern(regexp = "[A-Za-z]{3}") String currency,
                                       @Min(6) @Max(360) int capacity,
                                       @Size(max = 100) String aircraft) {
        private com.jetsetgo.service.RouteTemplateService.TemplateInput toInput() {
            return new com.jetsetgo.service.RouteTemplateService.TemplateInput(airline, flightNumber,
                    originCode, originCity, destinationCode, destinationCity, departureLocalTime,
                    durationMinutes, operatingDays, originTimeZone, destinationTimeZone,
                    baseFare, currency, capacity, aircraft);
        }
    }
}
