package com.jetsetgo.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Airport;

@Service
public class AirportDatasetImporter {
    private static final Logger log = LoggerFactory.getLogger(AirportDatasetImporter.class);
    private static final String DATASET_URL =
            "https://raw.githubusercontent.com/davidmegginson/ourairports-data/main/";
    private static final String IMPORTS_COLLECTION = "airport_dataset_imports";
    private static final String IMPORT_ID = "ourairports-iata-v1";
    private static final Pattern IATA_CODE = Pattern.compile("[A-Z]{3}");
    private static final Set<String> AIRPORT_TYPES =
            Set.of("large_airport", "medium_airport", "small_airport");
    private static final int BATCH_SIZE = 750;

    private final MongoTemplate mongo;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public AirportDatasetImporter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public int importIfNeeded() {
        Query markerQuery = Query.query(Criteria.where("_id").is(IMPORT_ID));
        if (mongo.exists(markerQuery, IMPORTS_COLLECTION)) {
            log.info("OurAirports catalog was already imported; skipping the one-time import.");
            return 0;
        }

        try {
            Map<String, String> countries = loadNames("countries.csv", "code", "name");
            Map<String, String> regions = loadNames("regions.csv", "code", "name");
            Map<String, Airport> airports = loadAirports(countries, regions);
            if (airports.isEmpty()) {
                throw new IllegalStateException("OurAirports returned no valid IATA airport records.");
            }

            List<Airport> batch = new ArrayList<>(BATCH_SIZE);
            for (Airport airport : airports.values()) {
                batch.add(airport);
                if (batch.size() == BATCH_SIZE) {
                    upsert(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                upsert(batch);
            }

            mongo.insert(new Document("_id", IMPORT_ID)
                    .append("source", DATASET_URL)
                    .append("airportCount", airports.size())
                    .append("importedAt", Instant.now()), IMPORTS_COLLECTION);
            log.info("Imported {} unique IATA airports from OurAirports.", airports.size());
            return airports.size();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the OurAirports CSV dataset.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OurAirports import was interrupted.", e);
        }
    }

    private Map<String, String> loadNames(String filename, String codeField, String nameField)
            throws IOException, InterruptedException {
        Map<String, String> names = new HashMap<>();
        try (CSVParser parser = parse(filename)) {
            for (CSVRecord record : parser) {
                String code = value(record, codeField).toUpperCase(Locale.ROOT);
                String name = value(record, nameField);
                if (!code.isBlank() && !name.isBlank()) {
                    names.put(code, name);
                }
            }
        }
        return names;
    }

    private Map<String, Airport> loadAirports(Map<String, String> countries, Map<String, String> regions)
            throws IOException, InterruptedException {
        try (CSVParser parser = parse("airports.csv")) {
            return mapAirportRecords(parser, countries, regions);
        }
    }

    static Map<String, Airport> mapAirportRecords(Iterable<CSVRecord> records,
                                                   Map<String, String> countries,
                                                   Map<String, String> regions) {
        Map<String, Airport> unique = new LinkedHashMap<>();
        for (CSVRecord record : records) {
            String type = value(record, "type");
            String code = value(record, "iata_code").toUpperCase(Locale.ROOT);
            String city = value(record, "municipality");
            String name = value(record, "name");
            String countryCode = value(record, "iso_country").toUpperCase(Locale.ROOT);
            if (!AIRPORT_TYPES.contains(type) || !IATA_CODE.matcher(code).matches()
                    || city.isBlank() || name.isBlank() || countryCode.isBlank()) {
                continue;
            }

            String regionCode = value(record, "iso_region").toUpperCase(Locale.ROOT);
            String regionName = regions.getOrDefault(regionCode, localRegion(regionCode, countryCode));
            Airport airport = new Airport(code, city, name, regionName,
                    countries.getOrDefault(countryCode, countryCode), countryCode, null,
                    "yes".equalsIgnoreCase(value(record, "scheduled_service")), type,
                    decimal(record, "latitude_deg"), decimal(record, "longitude_deg"),
                    "OURAIRPORTS");
            unique.merge(code, airport, (first, next) -> quality(next) > quality(first) ? next : first);
        }
        return unique;
    }

    private CSVParser parse(String filename) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(DATASET_URL + filename))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "JetSetGo-AirportCatalog/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("OurAirports request for " + filename + " returned HTTP "
                    + response.statusCode() + ".");
        }
        Reader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8);
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader);
    }

    private void upsert(List<Airport> airports) {
        BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Airport.class);
        for (Airport airport : airports) {
            Update update = new Update()
                    .set("city", airport.city())
                    .set("name", airport.name())
                    .set("stateOrRegion", airport.stateOrRegion())
                    .set("country", airport.country())
                    .set("countryCode", airport.countryCode())
                    .set("timeZone", airport.timeZone())
                    .set("scheduledService", airport.scheduledService())
                    .set("airportType", airport.airportType())
                    .set("latitude", airport.latitude())
                    .set("longitude", airport.longitude())
                    .set("dataSource", airport.dataSource());
            bulk.upsert(Query.query(Criteria.where("_id").is(airport.code())), update);
        }
        bulk.execute();
    }

    private static int quality(Airport airport) {
        int typeQuality = switch (airport.airportType()) {
            case "large_airport" -> 3;
            case "medium_airport" -> 2;
            default -> 1;
        };
        return (Boolean.TRUE.equals(airport.scheduledService()) ? 10 : 0) + typeQuality;
    }

    private static String localRegion(String regionCode, String countryCode) {
        String prefix = countryCode + "-";
        return regionCode.startsWith(prefix) ? regionCode.substring(prefix.length()) : regionCode;
    }

    private static String value(CSVRecord record, String field) {
        String value = record.get(field);
        return value == null ? "" : value.trim();
    }

    private static Double decimal(CSVRecord record, String field) {
        String value = value(record, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
