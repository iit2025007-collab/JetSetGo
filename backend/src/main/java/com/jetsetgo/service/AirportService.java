package com.jetsetgo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Airport;

@Service
public class AirportService {
    private final MongoTemplate mongo;

    public AirportService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<Airport> search(String search, String countryCode, boolean scheduledOnly) {
        Query query = new Query();
        List<Criteria> filters = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            for (String token : search.trim().split("\\s+")) {
                String safePattern = Pattern.quote(token);
                filters.add(new Criteria().orOperator(
                        Criteria.where("_id").regex(safePattern, "i"),
                        Criteria.where("city").regex(safePattern, "i"),
                        Criteria.where("name").regex(safePattern, "i"),
                        Criteria.where("stateOrRegion").regex(safePattern, "i"),
                        Criteria.where("country").regex(safePattern, "i"),
                        Criteria.where("countryCode").regex(safePattern, "i")));
            }
        }
        if (countryCode != null && !countryCode.isBlank()) {
            filters.add(Criteria.where("countryCode").is(countryCode.trim().toUpperCase(Locale.ROOT)));
        }
        if (scheduledOnly) {
            filters.add(Criteria.where("scheduledService").is(true));
        }
        if (!filters.isEmpty()) {
            query.addCriteria(filters.size() == 1
                    ? filters.get(0)
                    : new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        }
        return mongo.find(query.with(Sort.by(
                Sort.Order.desc("scheduledService"),
                Sort.Order.asc("airportType"),
                Sort.Order.asc("city"),
                Sort.Order.asc("_id"))).limit(30), Airport.class);
    }

    public List<String> countryCodes() {
        List<String> codes = mongo.getCollection("airports")
                .distinct("countryCode", String.class)
                .into(new ArrayList<>());
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }
}
