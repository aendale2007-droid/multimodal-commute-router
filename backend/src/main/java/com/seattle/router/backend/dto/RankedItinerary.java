package com.seattle.router.backend.dto;

import java.util.List;

public record RankedItinerary(
        String summary,
        long durationMinutes,
        double estimatedCostUsd,
        int transfers,
        double finalScore,
        List<RouteLeg> legs
) {
    public record Coord(double latitude, double longitude) {}

    public record RouteLeg(
            String mode,
            String fromName,
            String toName,
            long durationMinutes,
            double distanceMeters,
            String routeShortName,
            List<Coord> points
    ) {}
}

