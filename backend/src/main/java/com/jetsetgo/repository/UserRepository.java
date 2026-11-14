package com.jetsetgo.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.jetsetgo.domain.AppUser;

public interface UserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
