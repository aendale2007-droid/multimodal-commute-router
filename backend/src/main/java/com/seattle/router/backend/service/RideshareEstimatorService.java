package com.seattle.router.backend.service;

import com.seattle.router.backend.dto.RankedItinerary;
import com.seattle.router.backend.dto.RankedItinerary.RouteLeg;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RideshareEstimatorService {

    // Realistic Seattle UberX/Lyft pricing estimates
    private static final double BASE_FARE = 4.50;
    private static final double COST_PER_MILE = 1.65;
    private static final double COST_PER_MINUTE = 0.40;
    private static final double AVG_CITY_SPEED_MPH = 28.0; // Average speed

    public RouteLeg estimateRideshareLeg(double fromLat, double fromLon, String fromName,
                                         double toLat, double toLon, String toName) {
        double distanceMeters = haversineMeters(fromLat, fromLon, toLat, toLon);
        // Multiply straight-line distance by 1.25 to approximate real road network distance
        double roadDistanceMeters = distanceMeters * 1.25;
        double miles = roadDistanceMeters / 1609.34;

        long durationMinutes = Math.max(3, Math.round((miles / AVG_CITY_SPEED_MPH) * 60.0));
        double estimatedCost = Math.round((BASE_FARE + (miles * COST_PER_MILE) + (durationMinutes * COST_PER_MINUTE)) * 100.0) / 100.0;

        return new RouteLeg(
                "RIDESHARE",
                fromName,
                toName,
                durationMinutes,
                Math.round(roadDistanceMeters),
                String.format("Uber/Lyft (~$%.2f)", estimatedCost),
                List.of(
                        new RankedItinerary.Coord(fromLat, fromLon),
                        new RankedItinerary.Coord(toLat, toLon)
                )
        );

    }

    public RankedItinerary buildDirectRideshareItinerary(double fromLat, double fromLon, double toLat, double toLon) {
        RouteLeg leg = estimateRideshareLeg(fromLat, fromLon, "Origin", toLat, toLon, "Destination");
        double cost = extractCostFromLabel(leg.routeShortName());

        return new RankedItinerary(
                "RIDESHARE Direct (" + leg.routeShortName() + ")",
                leg.durationMinutes(),
                cost,
                0,
                0.0,
                List.of(leg)
        );
    }

    public double extractCostFromLabel(String label) {
        try {
            int dollarIdx = label.indexOf('$');
            int endIdx = label.indexOf(')');
            return Double.parseDouble(label.substring(dollarIdx + 1, endIdx));
        } catch (Exception e) {
            return 15.00;
        }
    }

    public double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
