package com.seattle.router.backend.controller;

import com.seattle.router.backend.dto.RankedItinerary;
import com.seattle.router.backend.dto.RouteRequest;
import com.seattle.router.backend.service.MultimodalPlannerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RouteController {

    private final Map<String, List<Map<String, Object>>> geocodeCache = new java.util.concurrent.ConcurrentHashMap<>();


    private final MultimodalPlannerService plannerService;
    private final RestClient otpClient;
    private final RestClient photonClient;
    private final ObjectMapper objectMapper;

    public RouteController(
            MultimodalPlannerService plannerService,
            ObjectMapper objectMapper,
            @Value("${otp.base-url}") String otpBaseUrl) {
        this.plannerService = plannerService;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        this.otpClient = RestClient.builder()
                .baseUrl(otpBaseUrl)
                .requestFactory(factory)
                .build();

        this.photonClient = RestClient.builder()
                .baseUrl("https://photon.komoot.io")
                .requestFactory(factory)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PugetSoundRouter/1.0")
                .defaultHeader("Accept", "application/json")
                .build();

    }

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "service", "Seattle Multimodal Router Backend");
    }

    @GetMapping("/geocode")
    public List<Map<String, Object>> geocode(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        String cacheKey = query.trim().toLowerCase();
        if (geocodeCache.containsKey(cacheKey)) {
            return geocodeCache.get(cacheKey);
        }

        List<Map<String, Object>> results = new ArrayList<>();

        // 1. Try Photon with browser-like User-Agent for addresses & landmarks
        try {
            String raw = photonClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/")
                            .queryParam("q", query.trim())
                            .queryParam("lat", 47.48)
                            .queryParam("lon", -122.30)
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (raw != null && !raw.isEmpty()) {
                JsonNode root = objectMapper.readTree(raw);
                for (JsonNode feature : root.path("features")) {
                    JsonNode props = feature.path("properties");
                    JsonNode coords = feature.path("geometry").path("coordinates");
                    if (coords.isArray() && coords.size() >= 2) {
                        double lon = coords.get(0).asDouble();
                        double lat = coords.get(1).asDouble();

                        String name = props.path("name").asText("");
                        String street = props.path("street").asText("");
                        String houseNumber = props.path("housenumber").asText("");
                        if (name.isEmpty()) name = (houseNumber + " " + street).trim();
                        if (name.isEmpty()) name = "Selected Location";

                        String city = props.path("city").asText(props.path("locality").asText(""));
                        String state = props.path("state").asText("");
                        String subtitle = String.join(", ",
                                List.of(street, city, state).stream().filter(s -> !s.isEmpty()).toList());

                        results.add(Map.of(
                                "name", name,
                                "subtitle", subtitle,
                                "lat", lat,
                                "lon", lon
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Photon lookup skipped: " + e.getMessage());
        }

        // 2. Also search local OTP2 transit stops & stations directly (always works offline!)
        if (results.isEmpty()) {
            try {
                String graphql = """
                    query FindStops($name: String!) {
                      stops(name: $name) {
                        name
                        lat
                        lon
                      }
                    }
                    """;
                String payload = objectMapper.writeValueAsString(Map.of(
                        "query", graphql,
                        "variables", Map.of("name", query.trim())
                ));
                String otpRaw = otpClient.post()
                        .uri("/otp/gtfs/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                JsonNode stops = objectMapper.readTree(otpRaw).path("data").path("stops");
                int count = 0;
                for (JsonNode stop : stops) {
                    if (count++ >= 5) break;
                    results.add(Map.of(
                            "name", stop.path("name").asText(),
                            "subtitle", "Transit Station / Stop",
                            "lat", stop.path("lat").asDouble(),
                            "lon", stop.path("lon").asDouble()
                    ));
                }
            } catch (Exception e) {
                System.err.println("OTP stop lookup failed: " + e.getMessage());
            }
        }

        return results;
    }

    @PostMapping("/plan")
    public List<RankedItinerary> planRoute(@RequestBody RouteRequest request) {
        return plannerService.planAndRankRoutes(request);
    }
}
