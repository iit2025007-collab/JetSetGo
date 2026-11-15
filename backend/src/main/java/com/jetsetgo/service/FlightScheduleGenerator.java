package com.jetsetgo.service;

import java.util.Set;
import java.time.LocalDate;
import java.util.List;

import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;

public interface FlightScheduleGenerator {
    int generateRollingWindow(LocalDate firstDate, int days);

    List<Flight> ensureDemoRouteInstances(Airport origin, Airport destination, LocalDate date);

    Flight createInstance(RouteTemplate template, LocalDate departureDate);

    int updateFutureInstances(RouteTemplate template, Set<String> protectedFlightIds);

    int setTemplateEnabled(String templateId, boolean enabled, Set<String> protectedFlightIds);
}
