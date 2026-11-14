package com.jetsetgo.service;

import java.util.List;
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

    public List<Airport> search(String search) {
        Query query = new Query();
        if (search != null && !search.isBlank()) {
            String safePattern = Pattern.quote(search.trim());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("_id").regex(safePattern, "i"),
                    Criteria.where("city").regex(safePattern, "i"),
                    Criteria.where("name").regex(safePattern, "i"),
                    Criteria.where("country").regex(safePattern, "i")));
        }
        return mongo.find(query.with(Sort.by("city", "_id")).limit(15), Airport.class);
    }
}
