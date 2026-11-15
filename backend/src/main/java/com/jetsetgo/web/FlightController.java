package com.jetsetgo.web;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.service.FlightService;
import com.jetsetgo.service.SeatInventoryService;

@RestController
@RequestMapping("/api")
public class FlightController {
    private final FlightService flights;
    private final SeatInventoryService seats;

    public FlightController(FlightService flights, SeatInventoryService seats) {
        this.flights = flights;
        this.seats = seats;
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("ok");
    }

    @GetMapping("/flights")
    public List<Flight> search(@RequestParam(required = false) String from,
                               @RequestParam(required = false) String to,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(defaultValue = "departure") String sort,
                               @RequestParam(required = false) String mode,
                               @RequestParam(required = false) String countryCode,
                               @RequestParam(required = false) String airline,
                               @RequestParam(required = false) BigDecimal maxPrice) {
        return flights.search(from, to, date, sort, mode, countryCode, airline, maxPrice);
    }

    @GetMapping("/flights/featured")
    public List<Flight> featured() {
        return flights.featuredDemoFlights();
    }

    @GetMapping("/flights/{id}")
    public Flight get(@PathVariable String id) {
        return flights.get(id);
    }

    @GetMapping("/flights/{id}/seats")
    public List<String> availableSeats(@PathVariable String id,
                                       @RequestParam(defaultValue = "ECONOMY") String travelClass) {
        return seats.availableSeats(flights.get(id), travelClass);
    }

    public record Health(String status) {}
}
