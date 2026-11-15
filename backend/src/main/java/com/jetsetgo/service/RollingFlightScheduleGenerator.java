package com.jetsetgo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.RouteTemplateRepository;

@Service
public class RollingFlightScheduleGenerator implements FlightScheduleGenerator {
    public static final int WINDOW_DAYS = 120;
    private final RouteTemplateRepository templates;
    private final MongoTemplate mongo;

    public RollingFlightScheduleGenerator(RouteTemplateRepository templates, MongoTemplate mongo) {
        this.templates = templates;
        this.mongo = mongo;
    }

    @Override
    public int generateRollingWindow(LocalDate firstDate, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Schedule window must contain at least one day");
        }
        Instant now = Instant.now();
        List<Flight> candidates = new ArrayList<>();
        for (RouteTemplate template : templates.findByActiveTrue()) {
            for (int offset = 0; offset < days; offset++) {
                LocalDate date = firstDate.plusDays(offset);
                if (!template.operatingDays().contains(date.getDayOfWeek().getValue())) {
                    continue;
                }
                Flight flight = createInstance(template, date);
                Instant departure = flight.departure();
                if (!departure.isAfter(now)) {
                    continue;
                }
                candidates.add(flight);
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }

        BulkOperations writes = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Flight.class);
        for (Flight flight : candidates) {
            Query byStableId = Query.query(Criteria.where("_id").is(flight.id()));
            writes.upsert(byStableId, new Update()
                    .setOnInsert("airline", flight.airline())
                    .setOnInsert("flightNumber", flight.flightNumber())
                    .setOnInsert("originCode", flight.originCode())
                    .setOnInsert("originCity", flight.originCity())
                    .setOnInsert("destinationCode", flight.destinationCode())
                    .setOnInsert("destinationCity", flight.destinationCity())
                    .setOnInsert("departure", flight.departure())
                    .setOnInsert("arrival", flight.arrival())
                    .setOnInsert("price", flight.price())
                    .setOnInsert("currency", flight.currency())
                    .setOnInsert("capacity", flight.capacity())
                    .setOnInsert("status", flight.status())
                    .setOnInsert("dataSource", flight.dataSource())
                    .setOnInsert("originTimeZone", flight.originTimeZone())
                    .setOnInsert("destinationTimeZone", flight.destinationTimeZone())
                    .setOnInsert("aircraft", flight.aircraft())
                    .setOnInsert("routeTemplateId", flight.routeTemplateId())
                    .setOnInsert("templateDisabled", false));
        }
        writes.execute();
        return candidates.size();
    }

    @Override
    public Flight createInstance(RouteTemplate template, LocalDate departureDate) {
        ZonedDateTime takeoff = departureDate.atTime(LocalTime.parse(template.departureLocalTime()))
                .atZone(ZoneId.of(template.originTimeZone()));
        ZonedDateTime arrival = takeoff.plusMinutes(template.durationMinutes())
                .withZoneSameInstant(ZoneId.of(template.destinationTimeZone()));
        return new Flight(template.id() + ":" + departureDate, template.airline(), template.flightNumber(),
                template.originCode(), template.originCity(), template.destinationCode(),
                template.destinationCity(), takeoff.toInstant(), arrival.toInstant(), template.baseFare(),
                template.currency(), template.capacity(), "SCHEDULED", "DEMO",
                template.originTimeZone(), template.destinationTimeZone(), template.aircraft(),
                null, null, template.id(), false);
    }

    @Override
    public int updateFutureInstances(RouteTemplate template, Set<String> protectedFlightIds) {
        Instant now = Instant.now();
        List<Flight> future = mongo.find(Query.query(Criteria.where("routeTemplateId").is(template.id())
                .and("departure").gt(now)), Flight.class);
        int updated = 0;
        for (Flight existing : future) {
            if (protectedFlightIds.contains(existing.id()) || hasReservedSeats(existing.id())) {
                continue;
            }
            LocalDate departureDate = existing.departure()
                    .atZone(ZoneId.of(existing.originTimeZone())).toLocalDate();
            if (!template.operatingDays().contains(departureDate.getDayOfWeek().getValue())) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(existing.id())),
                        Update.update("templateDisabled", true), Flight.class);
                continue;
            }
            Flight generated = createInstance(template, departureDate);
            Flight replacement = new Flight(existing.id(), generated.airline(), generated.flightNumber(),
                    generated.originCode(), generated.originCity(), generated.destinationCode(),
                    generated.destinationCity(), generated.departure(), generated.arrival(), generated.price(),
                    generated.currency(), generated.capacity(), existing.status(), generated.dataSource(),
                    generated.originTimeZone(), generated.destinationTimeZone(), generated.aircraft(),
                    generated.originTerminal(), generated.destinationTerminal(), template.id(),
                    !template.active());
            mongo.remove(Query.query(Criteria.where("flightId").is(existing.id())), SeatInventory.class);
            mongo.save(replacement);
            updated++;
        }
        return updated;
    }

    @Override
    public int setTemplateEnabled(String templateId, boolean enabled, Set<String> protectedFlightIds) {
        List<Flight> future = mongo.find(Query.query(Criteria.where("routeTemplateId").is(templateId)
                .and("departure").gt(Instant.now())), Flight.class);
        int updated = 0;
        for (Flight flight : future) {
            if (protectedFlightIds.contains(flight.id()) || hasReservedSeats(flight.id())
                    || Boolean.valueOf(!enabled).equals(flight.templateDisabled())) {
                continue;
            }
            mongo.updateFirst(Query.query(Criteria.where("_id").is(flight.id())),
                    Update.update("templateDisabled", !enabled), Flight.class);
            updated++;
        }
        return updated;
    }

    private boolean hasReservedSeats(String flightId) {
        return mongo.exists(Query.query(Criteria.where("flightId").is(flightId)
                .and("available").is(false)), SeatInventory.class);
    }

    public void generateRollingWindow() {
        generateRollingWindow(LocalDate.now(), WINDOW_DAYS);
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    public void extendScheduleDaily() {
        generateRollingWindow();
    }
}
