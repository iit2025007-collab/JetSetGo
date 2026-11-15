package com.jetsetgo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("airports")
@CompoundIndex(name = "airport_country_service_city",
        def = "{'countryCode': 1, 'scheduledService': -1, 'city': 1}")
public record Airport(
        @Id String code,
        String city,
        String name,
        String stateOrRegion,
        String country,
        String countryCode,
        String timeZone,
        Boolean scheduledService,
        String airportType,
        Double latitude,
        Double longitude,
        String dataSource) {

    public Airport(String code, String city, String name, String country, String timeZone) {
        this(code, city, name, "", country, "", timeZone, true, "medium_airport",
                null, null, "CURATED");
    }
}
