package com.jetsetgo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public record AppUser(@Id String id, @Indexed(unique = true) String email,
                      String passwordHash, String name, Role role) {
    public enum Role { CUSTOMER, ADMIN }
}
