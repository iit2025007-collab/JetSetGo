package com.jetsetgo.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.jetsetgo.domain.RouteTemplate;

public interface RouteTemplateRepository extends MongoRepository<RouteTemplate, String> {
    List<RouteTemplate> findByActiveTrue();
}
