package com.jetsetgo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("airports")
public record Airport(@Id String code, String city, String name, String country, String timeZone) {
}
