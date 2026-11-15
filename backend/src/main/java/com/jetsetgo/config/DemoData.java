package com.jetsetgo.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jetsetgo.domain.AppUser;
import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.FlightRepository;
import com.jetsetgo.repository.UserRepository;
import com.jetsetgo.service.AirportDatasetImporter;

@Configuration
public class DemoData {
    @Bean
    ApplicationRunner seedFlights(FlightRepository flights, MongoTemplate mongo,
                                  AirportRepository airports, UserRepository users,
                                  AirportDatasetImporter airportDatasetImporter,
                                  PasswordEncoder passwords,
                                  @Value("${app.seed-demo-data:false}") boolean seedDemoData,
                                  @Value("${app.import-airport-data:false}") boolean importAirportData) {
        return args -> {
            if (importAirportData) {
                airportDatasetImporter.importIfNeeded();
            }
            if (seedDemoData && airports.count() == 0) {
                airports.saveAll(List.of(
                        airport("DEL", "Delhi", "Indira Gandhi International Airport", "Delhi", "India", "IN", "Asia/Kolkata"),
                        airport("BOM", "Mumbai", "Chhatrapati Shivaji Maharaj International Airport", "Maharashtra", "India", "IN", "Asia/Kolkata"),
                        airport("BLR", "Bengaluru", "Kempegowda International Airport", "Karnataka", "India", "IN", "Asia/Kolkata"),
                        airport("HYD", "Hyderabad", "Rajiv Gandhi International Airport", "Telangana", "India", "IN", "Asia/Kolkata"),
                        airport("MAA", "Chennai", "Chennai International Airport", "Tamil Nadu", "India", "IN", "Asia/Kolkata"),
                        airport("CCU", "Kolkata", "Netaji Subhas Chandra Bose International Airport", "West Bengal", "India", "IN", "Asia/Kolkata"),
                        airport("LKO", "Lucknow", "Chaudhary Charan Singh International Airport", "Uttar Pradesh", "India", "IN", "Asia/Kolkata"),
                        airport("GAU", "Guwahati", "Lokpriya Gopinath Bordoloi International Airport", "Assam", "India", "IN", "Asia/Kolkata"),
                        airport("GOI", "Goa", "Dabolim Airport", "Goa", "India", "IN", "Asia/Kolkata"),
                        airport("GOX", "Goa", "Manohar International Airport", "Goa", "India", "IN", "Asia/Kolkata"),
                        airport("PNQ", "Pune", "Pune Airport", "Maharashtra", "India", "IN", "Asia/Kolkata"),
                        airport("DXB", "Dubai", "Dubai International Airport", "Dubai", "United Arab Emirates", "AE", "Asia/Dubai"),
                        airport("SIN", "Singapore", "Singapore Changi Airport", "Singapore", "Singapore", "SG", "Asia/Singapore"),
                        airport("LHR", "London", "Heathrow Airport", "England", "United Kingdom", "GB", "Europe/London"),
                        airport("LGW", "London", "Gatwick Airport", "England", "United Kingdom", "GB", "Europe/London"),
                        airport("JFK", "New York", "John F. Kennedy International Airport", "New York", "United States", "US", "America/New_York"),
                        airport("EWR", "New York", "Newark Liberty International Airport", "New Jersey", "United States", "US", "America/New_York"),
                        airport("LGA", "New York", "LaGuardia Airport", "New York", "United States", "US", "America/New_York"),
                        airport("LAX", "Los Angeles", "Los Angeles International Airport", "California", "United States", "US", "America/Los_Angeles"),
                        airport("NRT", "Tokyo", "Narita International Airport", "Chiba", "Japan", "JP", "Asia/Tokyo"),
                        airport("HND", "Tokyo", "Tokyo Haneda Airport", "Tokyo", "Japan", "JP", "Asia/Tokyo"),
                        airport("KUL", "Kuala Lumpur", "Kuala Lumpur International Airport", "Selangor", "Malaysia", "MY", "Asia/Kuala_Lumpur")
                ));
            }
            if (seedDemoData) {
                List<Flight> samples = List.of(
                        flight("air-india-101", "Air India", "AI 101", "DEL", "New Delhi", "BOM", "Mumbai",
                                "Asia/Kolkata", "Asia/Kolkata", 2, LocalTime.of(9, 15), LocalTime.of(11, 25), "6499"),
                        flight("indigo-204", "IndiGo", "6E 204", "BOM", "Mumbai", "BLR", "Bengaluru",
                                "Asia/Kolkata", "Asia/Kolkata", 3, LocalTime.of(14, 0), LocalTime.of(15, 40), "5199"),
                        flight("indigo-611", "IndiGo", "6E 611", "BLR", "Bengaluru", "DEL", "New Delhi",
                                "Asia/Kolkata", "Asia/Kolkata", 4, LocalTime.of(7, 30), LocalTime.of(10, 15), "7299"),
                        flight("air-india-222", "Air India", "AI 222", "DEL", "New Delhi", "BLR", "Bengaluru",
                                "Asia/Kolkata", "Asia/Kolkata", 5, LocalTime.of(18, 10), LocalTime.of(20, 55), "6899"),
                        flight("indigo-430", "IndiGo", "6E 430", "LKO", "Lucknow", "HYD", "Hyderabad",
                                "Asia/Kolkata", "Asia/Kolkata", 2, LocalTime.of(8, 20), LocalTime.of(10, 25), "5899"),
                        flight("indigo-512", "IndiGo", "6E 512", "BLR", "Bengaluru", "MAA", "Chennai",
                                "Asia/Kolkata", "Asia/Kolkata", 2, LocalTime.of(11, 0), LocalTime.of(12, 5), "3799"),
                        flight("air-india-381", "Air India", "AI 381", "CCU", "Kolkata", "GAU", "Guwahati",
                                "Asia/Kolkata", "Asia/Kolkata", 2, LocalTime.of(9, 45), LocalTime.of(10, 55), "4299"),
                        flight("air-india-990", "Air India", "AI 990", "DEL", "New Delhi", "DXB", "Dubai",
                                "Asia/Kolkata", "Asia/Dubai", 2, LocalTime.of(22, 30), LocalTime.of(1, 25), "17999"),
                        flight("air-india-129", "Air India", "AI 129", "BOM", "Mumbai", "LHR", "London",
                                "Asia/Kolkata", "Europe/London", 2, LocalTime.of(2, 15), LocalTime.of(7, 20), "42999"),
                        flight("indigo-510", "IndiGo", "6E 510", "MAA", "Chennai", "SIN", "Singapore",
                                "Asia/Kolkata", "Asia/Singapore", 2, LocalTime.of(23, 30), LocalTime.of(6, 40), "15999"),
                        flight("indigo-520", "IndiGo", "6E 520", "BLR", "Bengaluru", "SIN", "Singapore",
                                "Asia/Kolkata", "Asia/Singapore", 2, LocalTime.of(23, 0), LocalTime.of(6, 20), "16999"),
                        flight("air-india-342", "Air India", "AI 342", "BLR", "Bengaluru", "HND", "Tokyo",
                                "Asia/Kolkata", "Asia/Tokyo", 2, LocalTime.of(0, 30), LocalTime.of(12, 15), "34999"),
                        flight("demo-us-201", "Jet Set Go Demo Air", "JG 201", "JFK", "New York", "LAX", "Los Angeles",
                                "America/New_York", "America/Los_Angeles", 2, LocalTime.of(8, 30), LocalTime.of(11, 50), "28999"),
                        flight("indigo-408", "IndiGo", "6E 408", "LKO", "Lucknow", "BLR", "Bengaluru",
                                "Asia/Kolkata", "Asia/Kolkata", 2, LocalTime.of(13, 0), LocalTime.of(15, 45), "8199"),
                        flight("demo-return-101", "Air India", "AI 102", "BOM", "Mumbai", "DEL", "New Delhi",
                                "Asia/Kolkata", "Asia/Kolkata", 7, LocalTime.of(11, 15), LocalTime.of(13, 25), "6799"),
                        flight("demo-return-430", "IndiGo", "6E 431", "HYD", "Hyderabad", "LKO", "Lucknow",
                                "Asia/Kolkata", "Asia/Kolkata", 7, LocalTime.of(14, 0), LocalTime.of(16, 5), "5999"),
                        flight("demo-return-512", "IndiGo", "6E 513", "MAA", "Chennai", "BLR", "Bengaluru",
                                "Asia/Kolkata", "Asia/Kolkata", 7, LocalTime.of(16, 0), LocalTime.of(17, 5), "3899"),
                        flight("demo-return-381", "Air India", "AI 382", "GAU", "Guwahati", "CCU", "Kolkata",
                                "Asia/Kolkata", "Asia/Kolkata", 7, LocalTime.of(12, 0), LocalTime.of(13, 10), "4399"),
                        flight("demo-return-990", "Air India", "AI 991", "DXB", "Dubai", "DEL", "New Delhi",
                                "Asia/Dubai", "Asia/Kolkata", 7, LocalTime.of(13, 30), LocalTime.of(19, 0), "18499"),
                        flight("demo-return-129", "Air India", "AI 130", "LHR", "London", "BOM", "Mumbai",
                                "Europe/London", "Asia/Kolkata", 7, LocalTime.of(9, 0), LocalTime.of(23, 15), "43999"),
                        flight("demo-return-510", "IndiGo", "6E 511", "SIN", "Singapore", "MAA", "Chennai",
                                "Asia/Singapore", "Asia/Kolkata", 7, LocalTime.of(20, 0), LocalTime.of(22, 0), "16499"),
                        flight("demo-return-520", "IndiGo", "6E 521", "SIN", "Singapore", "BLR", "Bengaluru",
                                "Asia/Singapore", "Asia/Kolkata", 7, LocalTime.of(19, 0), LocalTime.of(22, 0), "17499"),
                        flight("demo-return-342", "Air India", "AI 343", "HND", "Tokyo", "BLR", "Bengaluru",
                                "Asia/Tokyo", "Asia/Kolkata", 7, LocalTime.of(10, 0), LocalTime.of(17, 0), "35499"),
                        flight("demo-us-202", "Jet Set Go Demo Air", "JG 202", "LAX", "Los Angeles", "JFK", "New York",
                                "America/Los_Angeles", "America/New_York", 7, LocalTime.of(15, 0), LocalTime.of(23, 20), "29499"),
                        flight("demo-return-408", "IndiGo", "6E 409", "BLR", "Bengaluru", "LKO", "Lucknow",
                                "Asia/Kolkata", "Asia/Kolkata", 7, LocalTime.of(16, 30), LocalTime.of(19, 15), "8299")
                );
                List<Flight> missingFlights = samples.stream()
                        .filter(flight -> !flights.existsById(flight.id()))
                        .toList();
                if (!missingFlights.isEmpty()) {
                    flights.saveAll(missingFlights);
                }
                List<SeatInventory> seats = new ArrayList<>();
                for (Flight flight : samples) {
                    if (mongo.exists(Query.query(Criteria.where("flightId").is(flight.id())), SeatInventory.class)) {
                        continue;
                    }
                    for (int row = 1; row <= 8; row++) {
                        for (char column = 'A'; column <= 'F'; column++) {
                            String number = row + String.valueOf(column);
                            seats.add(new SeatInventory(SeatInventory.idFor(flight.id(), number),
                                    flight.id(), number, true));
                        }
                    }
                }
                if (!seats.isEmpty()) {
                    mongo.insertAll(seats);
                }
            }
            String adminEmail = System.getenv("DEMO_ADMIN_EMAIL");
            String adminPassword = System.getenv("DEMO_ADMIN_PASSWORD");
            if (adminEmail != null && !adminEmail.isBlank()
                    && adminPassword != null && adminPassword.length() >= 10
                    && !users.existsByEmailIgnoreCase(adminEmail)) {
                users.save(new AppUser(null, adminEmail.trim().toLowerCase(),
                        passwords.encode(adminPassword), "Jet Set Go Admin", AppUser.Role.ADMIN));
            }
        };
    }

    private static Airport airport(String code, String city, String name, String stateOrRegion,
                                   String country, String countryCode, String timeZone) {
        return new Airport(code, city, name, stateOrRegion, country, countryCode, timeZone,
                true, "large_airport", null, null, "CURATED");
    }

    private static Flight flight(String id, String airline, String number, String originCode,
                                 String origin, String destinationCode, String destination,
                                 String originZone, String destinationZone, int dayOffset,
                                 LocalTime departure, LocalTime arrival, String price) {
        ZoneId originTimeZone = ZoneId.of(originZone);
        ZoneId destinationTimeZone = ZoneId.of(destinationZone);
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(dayOffset);
        ZonedDateTime takeoff = date.atTime(departure).atZone(originTimeZone);
        ZonedDateTime landing = date.atTime(arrival).atZone(destinationTimeZone);
        if (!landing.toInstant().isAfter(takeoff.toInstant())) {
            landing = landing.plusDays(1);
        }
        return new Flight(id, airline, number, originCode, origin, destinationCode, destination,
                takeoff.toInstant(), landing.toInstant(), new BigDecimal(price), "INR", 48, "SCHEDULED", "DEMO",
                originZone, destinationZone);
    }
}
