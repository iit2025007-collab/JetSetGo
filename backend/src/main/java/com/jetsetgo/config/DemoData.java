package com.jetsetgo.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jetsetgo.domain.AppUser;
import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.FlightRepository;
import com.jetsetgo.repository.RouteTemplateRepository;
import com.jetsetgo.repository.UserRepository;
import com.jetsetgo.service.AirportDatasetImporter;
import com.jetsetgo.service.RollingFlightScheduleGenerator;

@Configuration
public class DemoData {
    @Bean
    ApplicationRunner seedFlights(FlightRepository flights, AirportRepository airports,
                                  RouteTemplateRepository routeTemplates,
                                  RollingFlightScheduleGenerator scheduleGenerator, UserRepository users,
                                  AirportDatasetImporter airportDatasetImporter,
                                  PasswordEncoder passwords,
                                  @Value("${app.seed-demo-data:false}") boolean seedDemoData,
                                  @Value("${app.import-airport-data:false}") boolean importAirportData) {
        return args -> {
            if (importAirportData) {
                airportDatasetImporter.importIfNeeded();
            }
            if (seedDemoData) {
                seedCuratedAirports(airports);
                seedRouteTemplates(routeTemplates);
                scheduleGenerator.generateRollingWindow();
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

    static List<Airport> seedCuratedAirports(AirportRepository airports) {
        List<Airport> curated = List.of(
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
                        airport("AUH", "Abu Dhabi", "Zayed International Airport", "Abu Dhabi", "United Arab Emirates", "AE", "Asia/Dubai"),
                        airport("SIN", "Singapore", "Singapore Changi Airport", "Singapore", "Singapore", "SG", "Asia/Singapore"),
                        airport("LHR", "London", "Heathrow Airport", "England", "United Kingdom", "GB", "Europe/London"),
                        airport("LGW", "London", "Gatwick Airport", "England", "United Kingdom", "GB", "Europe/London"),
                        airport("JFK", "New York", "John F. Kennedy International Airport", "New York", "United States", "US", "America/New_York"),
                        airport("EWR", "New York", "Newark Liberty International Airport", "New Jersey", "United States", "US", "America/New_York"),
                        airport("LGA", "New York", "LaGuardia Airport", "New York", "United States", "US", "America/New_York"),
                        airport("LAX", "Los Angeles", "Los Angeles International Airport", "California", "United States", "US", "America/Los_Angeles"),
                        airport("NRT", "Tokyo", "Narita International Airport", "Chiba", "Japan", "JP", "Asia/Tokyo"),
                        airport("HND", "Tokyo", "Tokyo Haneda Airport", "Tokyo", "Japan", "JP", "Asia/Tokyo"),
                        airport("KUL", "Kuala Lumpur", "Kuala Lumpur International Airport", "Selangor", "Malaysia", "MY", "Asia/Kuala_Lumpur"),
                        airport("AMD", "Ahmedabad", "Sardar Vallabhbhai Patel International Airport", "Gujarat", "India", "IN", "Asia/Kolkata"),
                        airport("JAI", "Jaipur", "Jaipur International Airport", "Rajasthan", "India", "IN", "Asia/Kolkata"),
                        airport("COK", "Kochi", "Cochin International Airport", "Kerala", "India", "IN", "Asia/Kolkata"),
                        airport("BBI", "Bhubaneswar", "Biju Patnaik International Airport", "Odisha", "India", "IN", "Asia/Kolkata"),
                        airport("PAT", "Patna", "Jay Prakash Narayan Airport", "Bihar", "India", "IN", "Asia/Kolkata"),
                        airport("VNS", "Varanasi", "Lal Bahadur Shastri International Airport", "Uttar Pradesh", "India", "IN", "Asia/Kolkata"),
                        airport("IXC", "Chandigarh", "Shaheed Bhagat Singh International Airport", "Chandigarh", "India", "IN", "Asia/Kolkata"),
                        airport("IXD", "Allahabad", "Prayagraj Airport", "Uttar Pradesh", "India", "IN", "Asia/Kolkata"),
                        airport("BKK", "Bangkok", "Suvarnabhumi Airport", "Bangkok", "Thailand", "TH", "Asia/Bangkok"),
                        airport("DOH", "Doha", "Hamad International Airport", "Doha", "Qatar", "QA", "Asia/Qatar"),
                        airport("CDG", "Paris", "Charles de Gaulle Airport", "Île-de-France", "France", "FR", "Europe/Paris"));
        Map<String, Airport> existing = new HashMap<>();
        airports.findAllById(curated.stream().map(Airport::code).toList())
                .forEach(airport -> existing.put(airport.code(), airport));
        List<Airport> missingOrIncomplete = curated.stream()
                .filter(airport -> {
                    Airport saved = existing.get(airport.code());
                    return saved == null || isBlank(saved.countryCode()) || isBlank(saved.country())
                            || isBlank(saved.dataSource());
                })
                .toList();
        if (!missingOrIncomplete.isEmpty()) {
            airports.saveAll(missingOrIncomplete);
        }
        return missingOrIncomplete;
    }

    static List<RouteTemplate> seedRouteTemplates(RouteTemplateRepository templates) {
        List<Integer> daily = List.of(1, 2, 3, 4, 5, 6, 7);
        List<RouteTemplate> configured = List.of(
                route("demo-del-bom", "JG 101", "DEL", "Delhi", "BOM", "Mumbai", "09:15", 130, "Asia/Kolkata", "Asia/Kolkata", "6499", daily),
                route("demo-bom-del", "JG 102", "BOM", "Mumbai", "DEL", "Delhi", "20:00", 135, "Asia/Kolkata", "Asia/Kolkata", "6799", daily),
                route("demo-lko-hyd", "JG 201", "LKO", "Lucknow", "HYD", "Hyderabad", "08:20", 125, "Asia/Kolkata", "Asia/Kolkata", "5899", daily),
                route("demo-hyd-lko", "JG 202", "HYD", "Hyderabad", "LKO", "Lucknow", "14:00", 125, "Asia/Kolkata", "Asia/Kolkata", "5999", daily),
                route("demo-blr-maa", "JG 301", "BLR", "Bengaluru", "MAA", "Chennai", "11:00", 65, "Asia/Kolkata", "Asia/Kolkata", "3799", daily),
                route("demo-maa-blr", "JG 302", "MAA", "Chennai", "BLR", "Bengaluru", "16:00", 65, "Asia/Kolkata", "Asia/Kolkata", "3899", daily),
                route("demo-ccu-gau", "JG 401", "CCU", "Kolkata", "GAU", "Guwahati", "09:45", 70, "Asia/Kolkata", "Asia/Kolkata", "4299", daily),
                route("demo-gau-ccu", "JG 402", "GAU", "Guwahati", "CCU", "Kolkata", "12:00", 70, "Asia/Kolkata", "Asia/Kolkata", "4399", daily),
                route("demo-del-ixd", "JG 501", "DEL", "Delhi", "IXD", "Prayagraj", "07:30", 80, "Asia/Kolkata", "Asia/Kolkata", "3999", daily),
                route("demo-amd-jai", "JG 601", "AMD", "Ahmedabad", "JAI", "Jaipur", "10:00", 95, "Asia/Kolkata", "Asia/Kolkata", "4499", daily),
                route("demo-jai-amd", "JG 602", "JAI", "Jaipur", "AMD", "Ahmedabad", "17:00", 95, "Asia/Kolkata", "Asia/Kolkata", "4599", daily),
                route("demo-cok-bbi", "JG 701", "COK", "Kochi", "BBI", "Bhubaneswar", "08:00", 145, "Asia/Kolkata", "Asia/Kolkata", "6299", daily),
                route("demo-bbi-cok", "JG 702", "BBI", "Bhubaneswar", "COK", "Kochi", "15:30", 145, "Asia/Kolkata", "Asia/Kolkata", "6399", daily),
                route("demo-pat-vns", "JG 801", "PAT", "Patna", "VNS", "Varanasi", "09:30", 60, "Asia/Kolkata", "Asia/Kolkata", "3299", daily),
                route("demo-vns-pat", "JG 802", "VNS", "Varanasi", "PAT", "Patna", "13:00", 60, "Asia/Kolkata", "Asia/Kolkata", "3399", daily),
                route("demo-ixc-del", "JG 901", "IXC", "Chandigarh", "DEL", "Delhi", "07:00", 55, "Asia/Kolkata", "Asia/Kolkata", "2999", daily),
                route("demo-pnq-goi", "JG 1001", "PNQ", "Pune", "GOI", "Goa", "10:00", 65, "Asia/Kolkata", "Asia/Kolkata", "3199", daily),
                route("demo-goi-pnq", "JG 1002", "GOI", "Goa", "PNQ", "Pune", "18:00", 65, "Asia/Kolkata", "Asia/Kolkata", "3299", daily),
                route("demo-gox-hyd", "JG 1003", "GOX", "Goa", "HYD", "Hyderabad", "12:00", 75, "Asia/Kolkata", "Asia/Kolkata", "3499", daily),
                route("demo-hyd-gox", "JG 1004", "HYD", "Hyderabad", "GOX", "Goa", "19:00", 75, "Asia/Kolkata", "Asia/Kolkata", "3599", daily),
                route("demo-del-dxb", "JG 1101", "DEL", "Delhi", "DXB", "Dubai", "22:30", 225, "Asia/Kolkata", "Asia/Dubai", "17999", daily),
                route("demo-dxb-del", "JG 1102", "DXB", "Dubai", "DEL", "Delhi", "13:30", 210, "Asia/Dubai", "Asia/Kolkata", "18499", daily),
                route("demo-bom-lhr", "JG 1201", "BOM", "Mumbai", "LHR", "London", "02:15", 555, "Asia/Kolkata", "Europe/London", "42999", daily),
                route("demo-lhr-bom", "JG 1202", "LHR", "London", "BOM", "Mumbai", "09:00", 540, "Europe/London", "Asia/Kolkata", "43999", daily),
                route("demo-maa-sin", "JG 1301", "MAA", "Chennai", "SIN", "Singapore", "23:30", 250, "Asia/Kolkata", "Asia/Singapore", "15999", daily),
                route("demo-sin-maa", "JG 1302", "SIN", "Singapore", "MAA", "Chennai", "20:00", 240, "Asia/Singapore", "Asia/Kolkata", "16499", daily),
                route("demo-blr-hnd", "JG 1401", "BLR", "Bengaluru", "HND", "Tokyo", "00:30", 525, "Asia/Kolkata", "Asia/Tokyo", "34999", daily),
                route("demo-hnd-blr", "JG 1402", "HND", "Tokyo", "BLR", "Bengaluru", "10:00", 510, "Asia/Tokyo", "Asia/Kolkata", "35499", daily),
                route("demo-del-auh", "JG 1501", "DEL", "Delhi", "AUH", "Abu Dhabi", "21:45", 215, "Asia/Kolkata", "Asia/Dubai", "16999", daily),
                route("demo-auh-del", "JG 1502", "AUH", "Abu Dhabi", "DEL", "Delhi", "12:30", 205, "Asia/Dubai", "Asia/Kolkata", "17499", daily),
                route("demo-bom-doh", "JG 1601", "BOM", "Mumbai", "DOH", "Doha", "22:00", 225, "Asia/Kolkata", "Asia/Qatar", "15999", daily),
                route("demo-doh-bom", "JG 1602", "DOH", "Doha", "BOM", "Mumbai", "14:00", 220, "Asia/Qatar", "Asia/Kolkata", "16499", daily),
                route("demo-del-cdg", "JG 1701", "DEL", "Delhi", "CDG", "Paris", "01:00", 535, "Asia/Kolkata", "Europe/Paris", "38999", daily),
                route("demo-cdg-del", "JG 1702", "CDG", "Paris", "DEL", "Delhi", "11:00", 520, "Europe/Paris", "Asia/Kolkata", "39499", daily),
                route("demo-bom-bkk", "JG 1801", "BOM", "Mumbai", "BKK", "Bangkok", "23:00", 255, "Asia/Kolkata", "Asia/Bangkok", "13999", daily),
                route("demo-bkk-bom", "JG 1802", "BKK", "Bangkok", "BOM", "Mumbai", "18:00", 250, "Asia/Bangkok", "Asia/Kolkata", "14499", daily),
                route("demo-bom-kul", "JG 1851", "BOM", "Mumbai", "KUL", "Kuala Lumpur", "22:15", 300, "Asia/Kolkata", "Asia/Kuala_Lumpur", "14999", daily),
                route("demo-kul-bom", "JG 1852", "KUL", "Kuala Lumpur", "BOM", "Mumbai", "17:00", 295, "Asia/Kuala_Lumpur", "Asia/Kolkata", "15499", daily),
                route("demo-blr-nrt", "JG 1861", "BLR", "Bengaluru", "NRT", "Tokyo", "00:45", 530, "Asia/Kolkata", "Asia/Tokyo", "35999", daily),
                route("demo-nrt-blr", "JG 1862", "NRT", "Tokyo", "BLR", "Bengaluru", "10:30", 515, "Asia/Tokyo", "Asia/Kolkata", "36499", daily),
                route("demo-jfk-lax", "JG 1901", "JFK", "New York", "LAX", "Los Angeles", "08:30", 380, "America/New_York", "America/Los_Angeles", "28999", daily),
                route("demo-lax-jfk", "JG 1902", "LAX", "Los Angeles", "JFK", "New York", "15:00", 320, "America/Los_Angeles", "America/New_York", "29499", daily));
        List<String> ids = configured.stream().map(RouteTemplate::id).toList();
        java.util.Set<String> existing = templates.findAllById(ids).stream()
                .map(RouteTemplate::id).collect(java.util.stream.Collectors.toSet());
        List<RouteTemplate> missing = configured.stream()
                .filter(template -> !existing.contains(template.id())).toList();
        if (!missing.isEmpty()) {
            templates.saveAll(missing);
        }
        return missing;
    }

    private static RouteTemplate route(String id, String flightNumber,
                                       String originCode, String originCity,
                                       String destinationCode, String destinationCity,
                                       String departureLocalTime, int durationMinutes,
                                       String originTimeZone, String destinationTimeZone,
                                       String baseFare, List<Integer> operatingDays) {
        return new RouteTemplate(id, "JetSetGo Demonstration Air", flightNumber, originCode, originCity,
                destinationCode, destinationCity, departureLocalTime, durationMinutes, operatingDays,
                originTimeZone, destinationTimeZone, new BigDecimal(baseFare), "INR", 48,
                "Illustrative narrow-body aircraft", true);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
