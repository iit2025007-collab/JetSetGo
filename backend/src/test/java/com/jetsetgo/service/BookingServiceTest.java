package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.BookingRepository;
import com.jetsetgo.repository.FlightRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository bookings;
    @Mock FlightRepository flights;
    @Mock AirportRepository airports;
    @Mock MongoTemplate mongo;
    @Mock RazorpayService razorpay;
    @Mock DemoPaymentService demoPayments;
    @InjectMocks BookingService service;

    @Test
    void createsRoundTripForMultiplePassengersAndPricesSelectedClass() {
        Flight outbound = flight("out", "DEL", "DXB", 3, "1000");
        Flight returning = flight("return", "DXB", "DEL", 7, "1500");
        when(flights.findById("out")).thenReturn(Optional.of(outbound));
        when(flights.findById("return")).thenReturn(Optional.of(returning));
        when(airports.findById("DEL")).thenReturn(Optional.of(airport("DEL", "IN")));
        when(airports.findById("DXB")).thenReturn(Optional.of(airport("DXB", "AE")));
        when(bookings.findByUserIdAndIdempotencyKey("user", "request-key")).thenReturn(Optional.empty());
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(SeatInventory.class))).thenAnswer(invocation ->
                new SeatInventory("reserved", "flight", "seat", false));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = service.create("user", "request-key", new BookingService.CreateBooking(
                "out", "1A", "A. Passenger", "passenger@example.com", "return",
                List.of("1A", "1B"), List.of("2A", "2B"),
                List.of(
                        new BookingService.PassengerInput("A. Passenger", "IN", LocalDate.now().plusYears(2)),
                        new BookingService.PassengerInput("B. Passenger", "CA", LocalDate.now().plusYears(2))),
                "BUSINESS"));

        assertEquals(new BigDecimal("12500.00"), booking.amount());
        assertEquals("return", booking.tripDetails().returnFlightId());
        assertEquals(List.of("1A", "1B"), booking.tripDetails().seatNumbers());
        assertEquals(List.of("2A", "2B"), booking.tripDetails().returnSeatNumbers());
        assertEquals("BUSINESS", booking.tripDetails().travelClass());
        assertEquals(2, booking.tripDetails().passengers().size());
    }

    @Test
    void releasesOutboundSeatIfReturnSeatReservationFails() {
        Flight outbound = flight("out", "DEL", "DXB", 3, "1000");
        Flight returning = flight("return", "DXB", "DEL", 7, "1500");
        when(flights.findById("out")).thenReturn(Optional.of(outbound));
        when(flights.findById("return")).thenReturn(Optional.of(returning));
        when(airports.findById("DEL")).thenReturn(Optional.of(airport("DEL", "IN")));
        when(airports.findById("DXB")).thenReturn(Optional.of(airport("DXB", "AE")));
        when(bookings.findByUserIdAndIdempotencyKey("user", "request-key")).thenReturn(Optional.empty());
        AtomicInteger seatClaims = new AtomicInteger();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(SeatInventory.class))).thenAnswer(invocation -> seatClaims.getAndIncrement() == 0
                ? new SeatInventory("reserved", "out", "1A", false) : null);

        assertThrows(ResponseStatusException.class, () -> service.create("user", "request-key",
                new BookingService.CreateBooking("out", "1A", "A. Passenger", "passenger@example.com",
                        "return", List.of("1A"), List.of("2A"),
                        List.of(new BookingService.PassengerInput(
                                "A. Passenger", "IN", LocalDate.now().plusYears(2))), "ECONOMY")));

        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(SeatInventory.class));
    }

    private static Flight flight(String id, String origin, String destination, int days, String price) {
        Instant departure = Instant.now().plusSeconds(days * 24L * 60 * 60);
        return new Flight(id, "Demo Air", id, origin, origin, destination, destination,
                departure, departure.plusSeconds(3 * 60 * 60), new BigDecimal(price),
                "INR", 48, "SCHEDULED", "DEMO");
    }

    private static Airport airport(String code, String countryCode) {
        return new Airport(code, code, code + " airport", "", countryCode, countryCode,
                "UTC", true, "large_airport", null, null, "TEST");
    }
}
