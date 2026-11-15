package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;

import com.jetsetgo.domain.Airport;

class AirportDatasetImporterTest {
    @Test
    void mapsVerifiedIataAirportsAndKeepsScheduledServiceStatus() throws Exception {
        String csv = """
                type,iata_code,municipality,name,iso_country,iso_region,scheduled_service,latitude_deg,longitude_deg
                large_airport,ABC,Sample City,"Sample, International Airport",US,US-CA,yes,34.1,-118.4
                small_airport,ABC,Sample City,Alternate Field,US,US-CA,no,34.2,-118.5
                medium_airport,DEF,Other City,Other Airport,CA,CA-ON,no,43.6,-79.4
                heliport,HEL,Sample City,Heliport,US,US-CA,yes,34.0,-118.3
                small_airport,,Sample City,No IATA Code,US,US-CA,no,34.0,-118.3
                """;
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .build().parse(new StringReader(csv))) {
            Map<String, Airport> airports = AirportDatasetImporter.mapAirportRecords(
                    parser, Map.of("US", "United States", "CA", "Canada"),
                    Map.of("US-CA", "California", "CA-ON", "Ontario"));

            assertEquals(2, airports.size());
            assertEquals("Sample, International Airport", airports.get("ABC").name());
            assertEquals("California", airports.get("ABC").stateOrRegion());
            assertEquals("United States", airports.get("ABC").country());
            assertEquals("US", airports.get("ABC").countryCode());
            assertTrue(airports.get("ABC").scheduledService());
            assertFalse(airports.get("DEF").scheduledService());
            assertEquals("OURAIRPORTS", airports.get("DEF").dataSource());
        }
    }
}
