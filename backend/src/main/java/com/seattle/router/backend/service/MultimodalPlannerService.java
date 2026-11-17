package com.seattle.router.backend.service;

import com.seattle.router.backend.dto.RankedItinerary;
import com.seattle.router.backend.dto.RankedItinerary.RouteLeg;
import com.seattle.router.backend.dto.RouteRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MultimodalPlannerService {

    private final OtpClientService otpClient;
    private final RideshareEstimatorService rideshareService;

    // Major regional transit hubs (both Rail & Express Bus Transit Centers)
    private record TransitHub(String name, double lat, double lon) {}
    private static final List<TransitHub> SEATTLE_METRO_HUBS = List.of(
            new TransitHub("Federal Way Transit Center", 47.3168, -122.3046),
            new TransitHub("Angle Lake Station", 47.4227, -122.2977),
            new TransitHub("Tukwila Int'l Blvd Station", 47.4643, -122.2884),
            new TransitHub("Burien Transit Center", 47.4706, -122.3338),
            new TransitHub("Kent Station", 47.3842, -122.2335),
            new TransitHub("Downtown Seattle (Westlake)", 47.6114, -122.3375),
            new TransitHub("Northgate Transit Center", 47.7033, -122.3278),
            new TransitHub("Bellevue Transit Center", 47.6152, -122.1950)
    );

    public MultimodalPlannerService(OtpClientService otpClient, RideshareEstimatorService rideshareService) {
        this.otpClient = otpClient;
        this.rideshareService = rideshareService;
    }

    public List<RankedItinerary> planAndRankRoutes(RouteRequest req) {
        List<RankedItinerary> candidates = new ArrayList<>();

        // 1. Fetch standard OTP2 transit + walk routes (could be bus, rail, or combinations)
        candidates.addAll(otpClient.fetchTransitRoutes(req.originLat(), req.originLon(), req.destLat(), req.destLon()));

        // 2. Find the 2 closest transit hubs to the user's origin (if not already standing at one)
        List<TransitHub> nearestHubs = SEATTLE_METRO_HUBS.stream()
                .filter(hub -> rideshareService.haversineMeters(req.originLat(), req.originLon(), hub.lat(), hub.lon()) > 800) // >0.5 miles away
                .sorted(Comparator.comparingDouble(hub ->
                        rideshareService.haversineMeters(req.originLat(), req.originLon(), hub.lat(), hub.lon())))
                .limit(2)
                .toList();

        // 3. Stitch: First-Mile Rideshare -> Transit Hub -> OTP2 Transit (Bus or Train)
        for (TransitHub hub : nearestHubs) {
            RouteLeg uberLeg = rideshareService.estimateRideshareLeg(
                    req.originLat(), req.originLon(), "Origin", hub.lat(), hub.lon(), hub.name()
            );
            double uberCost = rideshareService.extractCostFromLabel(uberLeg.routeShortName());

            List<RankedItinerary> fromHubRoutes = otpClient.fetchTransitRoutes(hub.lat(), hub.lon(), req.destLat(), req.destLon());
            if (!fromHubRoutes.isEmpty()) {
                RankedItinerary bestTransitFromHub = fromHubRoutes.get(0); // Fastest option from that hub

                List<RouteLeg> combinedLegs = new ArrayList<>();
                combinedLegs.add(uberLeg);
                combinedLegs.addAll(bestTransitFromHub.legs());

                long totalDuration = uberLeg.durationMinutes() + bestTransitFromHub.durationMinutes() + 4; // +4 min transfer buffer
                double totalCost = Math.round((uberCost + bestTransitFromHub.estimatedCostUsd()) * 100.0) / 100.0;

                candidates.add(new RankedItinerary(
                        "RIDESHARE → " + bestTransitFromHub.summary(),
                        totalDuration,
                        totalCost,
                        bestTransitFromHub.transfers() + 1,
                        0.0,
                        combinedLegs
                ));
            }
        }

        // 4. Add Direct Door-to-Door Rideshare option
        candidates.add(rideshareService.buildDirectRideshareItinerary(
                req.originLat(), req.originLon(), req.destLat(), req.destLon()
        ));

        // 5. Deduplicate identical summaries & score all candidates based on user preferences
        return scoreAndSort(deduplicate(candidates), req);
    }

    private List<RankedItinerary> deduplicate(List<RankedItinerary> raw) {
        Map<String, RankedItinerary> unique = new LinkedHashMap<>();
        for (RankedItinerary item : raw) {
            unique.putIfAbsent(item.summary(), item);
        }
        return new ArrayList<>(unique.values());
    }

    private List<RankedItinerary> scoreAndSort(List<RankedItinerary> items, RouteRequest req) {
        if (items.isEmpty()) return items;

        long minTime = items.stream().mapToLong(RankedItinerary::durationMinutes).min().orElse(1);
        long maxTime = Math.max(minTime + 1, items.stream().mapToLong(RankedItinerary::durationMinutes).max().orElse(2));

        double minCost = items.stream().mapToDouble(RankedItinerary::estimatedCostUsd).min().orElse(0.0);
        double maxCost = Math.max(minCost + 1.0, items.stream().mapToDouble(RankedItinerary::estimatedCostUsd).max().orElse(1.0));

        List<RankedItinerary> scored = new ArrayList<>();
        for (RankedItinerary item : items) {
            // Normalize time & cost to a 0-100 scale (100 = fastest / cheapest)
            double timeScore = 100.0 * (1.0 - ((double) (item.durationMinutes() - minTime) / (maxTime - minTime)));
            double costScore = 100.0 * (1.0 - ((item.estimatedCostUsd() - minCost) / (maxCost - minCost)));

            // Apply user weights + small penalty per transfer
            double rawScore = (req.timeWeight() * timeScore) + (req.costWeight() * costScore) - (item.transfers() * 3.0);
            double finalScore = Math.max(0, Math.round(rawScore * 10.0) / 10.0);

            scored.add(new RankedItinerary(
                    item.summary(),
                    item.durationMinutes(),
                    item.estimatedCostUsd(),
                    item.transfers(),
                    finalScore,
                    item.legs()
            ));
        }

        scored.sort(Comparator.comparingDouble(RankedItinerary::finalScore).reversed());
        return scored;
    }
}
