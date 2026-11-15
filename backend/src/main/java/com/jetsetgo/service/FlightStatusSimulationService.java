package com.jetsetgo.service;

import java.time.Instant;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Flight;

@Service
public class FlightStatusSimulationService {
    private final MongoTemplate mongo;

    public FlightStatusSimulationService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Scheduled(fixedDelayString = "${app.flight-status-scan-ms:60000}")
    public void advanceDemoFlights() {
        Instant now = Instant.now();
        mongo.updateMulti(Query.query(Criteria.where("dataSource").is("DEMO")
                        .and("status").in("SCHEDULED", "DELAYED")
                        .and("departure").lte(now)),
                Update.update("status", "DEPARTED"), Flight.class);
        mongo.updateMulti(Query.query(Criteria.where("dataSource").is("DEMO")
                        .and("status").is("DEPARTED").and("arrival").lte(now)),
                Update.update("status", "COMPLETED"), Flight.class);
    }
}
