package com.jetsetgo.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jetsetgo.domain.Airport;
import com.jetsetgo.service.AirportService;

@RestController
@RequestMapping("/api/airports")
public class AirportController {
    private final AirportService airports;

    public AirportController(AirportService airports) {
        this.airports = airports;
    }

    @GetMapping
    public List<Airport> search(@RequestParam(required = false) String q,
                                @RequestParam(required = false) String countryCode,
                                @RequestParam(defaultValue = "false") boolean scheduledOnly) {
        return airports.search(q, countryCode, scheduledOnly);
    }

    @GetMapping("/countries")
    public List<String> countries() {
        return airports.countryCodes();
    }
}
