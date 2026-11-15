package com.jetsetgo.service;

import java.util.Set;
import java.time.LocalDate;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;

public interface FlightScheduleGenerator {
    int generateRollingWindow(LocalDate firstDate, int days);

    Flight createInstance(RouteTemplate template, LocalDate departureDate);

    int updateFutureInstances(RouteTemplate template, Set<String> protectedFlightIds);

    int setTemplateEnabled(String templateId, boolean enabled, Set<String> protectedFlightIds);
}
