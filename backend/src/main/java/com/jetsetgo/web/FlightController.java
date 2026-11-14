package com.jetsetgo.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.SeatInventoryRepository;
import com.jetsetgo.service.FlightService;

@RestController
@RequestMapping("/api")
public class FlightController {
    private final FlightService flights;
    private final SeatInventoryRepository seats;

    public FlightController(FlightService flights, SeatInventoryRepository seats) {
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
                               @RequestParam(defaultValue = "departure") String sort) {
        return flights.search(from, to, date, sort);
    }

    @GetMapping("/flights/{id}")
    public Flight get(@PathVariable String id) {
        return flights.get(id);
    }

    @GetMapping("/flights/{id}/seats")
    public List<String> availableSeats(@PathVariable String id) {
        flights.get(id);
        return seats.findByFlightIdAndAvailableTrueOrderBySeatNumber(id).stream()
                .map(SeatInventory::seatNumber).toList();
    }

    public record Health(String status) {}
}
