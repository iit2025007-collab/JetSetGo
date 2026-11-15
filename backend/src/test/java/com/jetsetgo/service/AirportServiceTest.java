package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.jetsetgo.domain.Airport;

@ExtendWith(MockitoExtension.class)
class AirportServiceTest {
    @Mock MongoTemplate mongo;
    @InjectMocks AirportService service;

    @Test
    void multiWordSearchMatchesEachTermAcrossAirportFields() {
        when(mongo.find(any(Query.class), eq(Airport.class))).thenReturn(List.of());

        service.search("Allahabad Airport", "IN", false);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Airport.class));
        Document queryObject = query.getValue().getQueryObject();
        List<?> filters = queryObject.getList("$and", Object.class);

        assertEquals(3, filters.size());
        assertTrue(((Document) filters.get(0)).containsKey("$or"));
        assertTrue(((Document) filters.get(1)).containsKey("$or"));
        assertTrue(queryObject.toJson().contains("Allahabad"));
        assertTrue(queryObject.toJson().contains("Airport"));
        assertEquals("IN", ((Document) filters.get(2)).getString("countryCode"));
    }
}
