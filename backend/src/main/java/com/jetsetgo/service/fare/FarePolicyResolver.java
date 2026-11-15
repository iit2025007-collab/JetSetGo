package com.jetsetgo.service.fare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FarePolicyResolver {
    private final Map<String, FarePolicy> policies;

    public FarePolicyResolver(List<FarePolicy> policies) {
        this.policies = policies.stream().collect(Collectors.toUnmodifiableMap(
                policy -> policy.travelClass().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public FarePolicy resolve(String travelClass) {
        String normalized = travelClass == null || travelClass.isBlank()
                ? "ECONOMY" : travelClass.trim().toUpperCase(Locale.ROOT);
        FarePolicy policy = policies.get(normalized);
        if (policy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported travel class");
        }
        return policy;
    }

    public BigDecimal calculate(String travelClass, BigDecimal baseFare, int passengerCount) {
        return resolve(travelClass).calculate(baseFare, passengerCount).setScale(2, RoundingMode.HALF_UP);
    }
}
