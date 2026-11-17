package com.seattle.router.backend.dto;

import java.util.List;

public record RankedItinerary(
        String summary,          // e.g., "Uber + 1 Line Light Rail" or "Bus + Walk"
        long durationMinutes,
        double estimatedCostUsd,
        int transfers,
        double finalScore,       // 0 to 100 based on user sliders
        List<RouteLeg> legs
) {
    public record RouteLeg(
            String mode,         // WALK, BUS, RAIL, SCOOTER, RIDESHARE
            String fromName,
            String toName,
            long durationMinutes,
            double distanceMeters,
            String routeShortName // e.g., "1 Line", "13", "Lime"
    ) {}
}
