package com.seattle.router.backend.dto;

public record RouteRequest(
        double originLat,
        double originLon,
        double destLat,
        double destLon,
        double timeWeight,   // 0.0 (save money) to 1.0 (save time)
        double costWeight    // e.g., 1.0 - timeWeight
) {}

