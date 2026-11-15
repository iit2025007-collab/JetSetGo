package com.jetsetgo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.RouteTemplateRepository;

@Service
public class RollingFlightScheduleGenerator implements FlightScheduleGenerator {
    public static final int WINDOW_DAYS = 120;
    private static final List<LocalTime> DEMO_DEPARTURE_TIMES =
            List.of(LocalTime.of(8, 0), LocalTime.of(13, 30), LocalTime.of(19, 0));
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

        upsertFlights(candidates);
        return candidates.size();
    }

    @Override
    public List<Flight> ensureDemoRouteInstances(Airport origin, Airport destination, LocalDate requestedDate) {
        if (origin == null || destination == null || origin.code() == null || destination.code() == null
                || origin.code().equalsIgnoreCase(destination.code())
                || Boolean.FALSE.equals(origin.scheduledService())
                || Boolean.FALSE.equals(destination.scheduledService())) {
            return List.of();
        }

        ZoneId originZone = timeZone(origin);
        ZoneId destinationZone = timeZone(destination);
        LocalDate today = LocalDate.now(originZone);
        LocalDate date = requestedDate == null ? today : requestedDate;
        if (requestedDate == null && DEMO_DEPARTURE_TIMES.stream()
                .noneMatch(time -> today.atTime(time).atZone(originZone).toInstant().isAfter(Instant.now()))) {
            date = today.plusDays(1);
        }
        if (date.isBefore(today) || date.isAfter(today.plusDays(WINDOW_DAYS - 1L))) {
            return List.of();
        }

        double distanceKm = distanceKm(origin, destination);
        int durationMinutes = Math.max(45, Math.min(18 * 60, (int) Math.ceil(distanceKm / 780.0 * 60) + 50));
        int flightNumberBase = 1000 + Math.floorMod(
                (origin.code() + destination.code()).hashCode(), 8000);
        Instant now = Instant.now();
        List<Flight> generated = new ArrayList<>();
        for (int index = 0; index < DEMO_DEPARTURE_TIMES.size(); index++) {
            ZonedDateTime departure = date.atTime(DEMO_DEPARTURE_TIMES.get(index)).atZone(originZone);
            if (!departure.toInstant().isAfter(now)) {
                continue;
            }
            ZonedDateTime arrival = departure.plusMinutes(durationMinutes).withZoneSameInstant(destinationZone);
            long fare = Math.max(3500L, Math.min(99999L,
                    3500L + Math.round(distanceKm * 4) + index * 700L));
            generated.add(new Flight(
                    "demo-route-" + origin.code().toUpperCase(Locale.ROOT) + "-"
                            + destination.code().toUpperCase(Locale.ROOT) + "-" + date + "-" + (index + 1),
                    "JetSetGo Demonstration Air",
                    "JG " + (flightNumberBase + index),
                    origin.code().toUpperCase(Locale.ROOT), origin.city(),
                    destination.code().toUpperCase(Locale.ROOT), destination.city(),
                    departure.toInstant(), arrival.toInstant(), java.math.BigDecimal.valueOf(fare),
                    "INR", 48, "SCHEDULED", "DEMO", originZone.getId(), destinationZone.getId(),
                    "Illustrative demo aircraft", null, null, null, false));
        }
        upsertFlights(generated);
        return generated;
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

    private static ZoneId timeZone(Airport airport) {
        if (airport.timeZone() != null && !airport.timeZone().isBlank()) {
            try {
                return ZoneId.of(airport.timeZone());
            } catch (java.time.DateTimeException ignored) {
                // Use a country-based demo timezone when the catalog value is invalid.
            }
        }
        String region = airport.stateOrRegion() == null ? "" : airport.stateOrRegion().toUpperCase(Locale.ROOT);
        return switch (airport.countryCode() == null ? "" : airport.countryCode().toUpperCase(Locale.ROOT)) {
            case "IN" -> ZoneId.of("Asia/Kolkata");
            case "AE" -> ZoneId.of("Asia/Dubai");
            case "SG" -> ZoneId.of("Asia/Singapore");
            case "GB" -> ZoneId.of("Europe/London");
            case "FR" -> ZoneId.of("Europe/Paris");
            case "JP" -> ZoneId.of("Asia/Tokyo");
            case "MY" -> ZoneId.of("Asia/Kuala_Lumpur");
            case "TH" -> ZoneId.of("Asia/Bangkok");
            case "QA" -> ZoneId.of("Asia/Qatar");
            case "US" -> region.equals("CA") || region.equals("WA") || region.equals("OR")
                    ? ZoneId.of("America/Los_Angeles")
                    : region.equals("HI") ? ZoneId.of("Pacific/Honolulu")
                    : region.equals("AK") ? ZoneId.of("America/Anchorage")
                    : region.equals("CO") || region.equals("AZ") || region.equals("UT")
                    ? ZoneId.of("America/Denver")
                    : region.equals("IL") || region.equals("TX") || region.equals("MN")
                    ? ZoneId.of("America/Chicago") : ZoneId.of("America/New_York");
            case "CA" -> region.equals("BC") ? ZoneId.of("America/Vancouver")
                    : region.equals("AB") ? ZoneId.of("America/Edmonton")
                    : ZoneId.of("America/Toronto");
            case "AU" -> region.equals("WA") ? ZoneId.of("Australia/Perth")
                    : region.equals("QLD") ? ZoneId.of("Australia/Brisbane")
                    : ZoneId.of("Australia/Sydney");
            default -> ZoneId.of("UTC");
        };
    }

    private static double distanceKm(Airport origin, Airport destination) {
        if (origin.latitude() == null || origin.longitude() == null
                || destination.latitude() == null || destination.longitude() == null) {
            return 900;
        }
        double latitudeDistance = Math.toRadians(destination.latitude() - origin.latitude());
        double longitudeDistance = Math.toRadians(destination.longitude() - origin.longitude());
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(origin.latitude())) * Math.cos(Math.toRadians(destination.latitude()))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        double clamped = Math.max(0, Math.min(1, a));
        return 6371 * 2 * Math.atan2(Math.sqrt(clamped), Math.sqrt(1 - clamped));
    }

    private void upsertFlights(List<Flight> candidates) {
        if (candidates.isEmpty()) {
            return;
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
