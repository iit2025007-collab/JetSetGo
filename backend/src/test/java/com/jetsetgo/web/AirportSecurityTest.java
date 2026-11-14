package com.jetsetgo.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jetsetgo.config.JwtAuthenticationFilter;
import com.jetsetgo.config.JwtService;
import com.jetsetgo.config.SecurityConfig;
import com.jetsetgo.domain.Airport;
import com.jetsetgo.service.AirportService;

@WebMvcTest(AirportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AirportSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AirportService airports;
    @MockitoBean JwtService jwtService;

    @Test
    void airportSearchIsPublicAndReturnsMatchingAirports() throws Exception {
        when(airports.search("HY")).thenReturn(List.of(new Airport(
                "HYD", "Hyderabad", "Rajiv Gandhi International Airport", "India", "Asia/Kolkata")));

        mvc.perform(get("/api/airports").param("q", "HY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("HYD"));

        verify(airports).search("HY");
    }

    @Test
    void bookingEndpointsRemainProtected() throws Exception {
        mvc.perform(get("/api/bookings")).andExpect(status().is4xxClientError());
    }
}
