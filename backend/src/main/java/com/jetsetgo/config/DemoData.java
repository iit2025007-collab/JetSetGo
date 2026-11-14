package com.jetsetgo.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jetsetgo.domain.AppUser;
import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.FlightRepository;
import com.jetsetgo.repository.UserRepository;

@Configuration
public class DemoData {
    @Bean
    ApplicationRunner seedFlights(FlightRepository flights, MongoTemplate mongo,
                                  AirportRepository airports, UserRepository users,
                                  PasswordEncoder passwords,
                                  @Value("${app.seed-demo-data:false}") boolean seedDemoData) {
        return args -> {
            if (seedDemoData && airports.count() == 0) {
                airports.saveAll(List.of(
                        airport("DEL", "Delhi", "Indira Gandhi International Airport", "India", "Asia/Kolkata"),
                        airport("BOM", "Mumbai", "Chhatrapati Shivaji Maharaj International Airport", "India", "Asia/Kolkata"),
                        airport("BLR", "Bengaluru", "Kempegowda International Airport", "India", "Asia/Kolkata"),
                        airport("HYD", "Hyderabad", "Rajiv Gandhi International Airport", "India", "Asia/Kolkata"),
                        airport("MAA", "Chennai", "Chennai International Airport", "India", "Asia/Kolkata"),
                        airport("CCU", "Kolkata", "Netaji Subhas Chandra Bose International Airport", "India", "Asia/Kolkata"),
                        airport("LKO", "Lucknow", "Chaudhary Charan Singh International Airport", "India", "Asia/Kolkata"),
                        airport("PNQ", "Pune", "Pune Airport", "India", "Asia/Kolkata"),
                        airport("DXB", "Dubai", "Dubai International Airport", "United Arab Emirates", "Asia/Dubai"),
                        airport("SIN", "Singapore", "Singapore Changi Airport", "Singapore", "Asia/Singapore"),
                        airport("LHR", "London", "Heathrow Airport", "United Kingdom", "Europe/London"),
                        airport("LGW", "London", "Gatwick Airport", "United Kingdom", "Europe/London"),
                        airport("JFK", "New York", "John F. Kennedy International Airport", "United States", "America/New_York"),
                        airport("EWR", "New York", "Newark Liberty International Airport", "United States", "America/New_York"),
                        airport("LGA", "New York", "LaGuardia Airport", "United States", "America/New_York")
                ));
            }
            if (seedDemoData && flights.count() == 0) {
                ZoneId zone = ZoneId.of("Asia/Kolkata");
                LocalDate today = LocalDate.now(zone);
                List<Flight> samples = List.of(
                        flight("air-india-101", "Air India", "AI 101", "DEL", "New Delhi", "BOM", "Mumbai",
                                today.plusDays(2), LocalTime.of(9, 15), LocalTime.of(11, 25), "6499"),
                        flight("indigo-204", "IndiGo", "6E 204", "BOM", "Mumbai", "BLR", "Bengaluru",
                                today.plusDays(3), LocalTime.of(14, 0), LocalTime.of(15, 40), "5199"),
                        flight("indigo-611", "IndiGo", "6E 611", "BLR", "Bengaluru", "DEL", "New Delhi",
                                today.plusDays(4), LocalTime.of(7, 30), LocalTime.of(10, 15), "7299"),
                        flight("air-india-222", "Air India", "AI 222", "DEL", "New Delhi", "BLR", "Bengaluru",
                                today.plusDays(5), LocalTime.of(18, 10), LocalTime.of(20, 55), "6899")
                );
                flights.saveAll(samples);
                List<SeatInventory> seats = new ArrayList<>();
                for (Flight flight : samples) {
                    for (int row = 1; row <= 8; row++) {
                        for (char column = 'A'; column <= 'F'; column++) {
                            String number = row + String.valueOf(column);
                            seats.add(new SeatInventory(SeatInventory.idFor(flight.id(), number),
                                    flight.id(), number, true));
                        }
                    }
                }
                mongo.insertAll(seats);
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

    private static Airport airport(String code, String city, String name, String country, String timeZone) {
        return new Airport(code, city, name, country, timeZone);
    }

    private static Flight flight(String id, String airline, String number, String originCode,
                                 String origin, String destinationCode, String destination,
                                 LocalDate date, LocalTime departure, LocalTime arrival, String price) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Instant takeoff = date.atTime(departure).atZone(zone).toInstant();
        Instant landing = date.atTime(arrival).atZone(zone).toInstant();
        return new Flight(id, airline, number, originCode, origin, destinationCode, destination,
                takeoff, landing, new BigDecimal(price), "INR", 48, "SCHEDULED", "DEMO");
    }
}
