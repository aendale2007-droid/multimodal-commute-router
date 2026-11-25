package com.seattle.router.backend.service;

import com.seattle.router.backend.dto.RankedItinerary;
import com.seattle.router.backend.dto.RankedItinerary.Coord;
import com.seattle.router.backend.dto.RankedItinerary.RouteLeg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OtpClientService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OtpClientService(@Value("${otp.base-url}") String otpBaseUrl, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        this.restClient = RestClient.builder()
                .baseUrl(otpBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    public List<RankedItinerary> fetchTransitRoutes(double fromLat, double fromLon, double toLat, double toLon) {
        String graphqlQuery = """
            query PlanRoute($fromLat: Float!, $fromLon: Float!, $toLat: Float!, $toLon: Float!) {
              plan(
                from: {lat: $fromLat, lon: $fromLon}
                to: {lat: $toLat, lon: $toLon}
                numItineraries: 4
              ) {
                itineraries {
                  duration
                  legs {
                    mode
                    distance
                    duration
                    from { name lat lon }
                    to { name lat lon }
                    route { shortName }
                    legGeometry { points }
                  }
                }
              }
            }
            """;

        try {
            Map<String, Object> requestBody = Map.of(
                    "query", graphqlQuery,
                    "variables", Map.of(
                            "fromLat", fromLat,
                            "fromLon", fromLon,
                            "toLat", toLat,
                            "toLon", toLon
                    )
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            String rawResponse = restClient.post()
                    .uri("/otp/gtfs/v1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonPayload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            return parseItineraries(root);
        } catch (Exception e) {
            System.err.println("Error calling OTP2: " + e.getMessage());
            return List.of();
        }
    }

    private List<RankedItinerary> parseItineraries(JsonNode root) {
        List<RankedItinerary> results = new ArrayList<>();
        if (root == null || !root.has("data")) return results;

        JsonNode itineraries = root.path("data").path("plan").path("itineraries");
        for (JsonNode itin : itineraries) {
            long totalMinutes = Math.round(itin.path("duration").asDouble() / 60.0);
            List<RouteLeg> legs = new ArrayList<>();
            int transitLegs = 0;
            boolean usedTransit = false;

            for (JsonNode legNode : itin.path("legs")) {
                String mode = legNode.path("mode").asText();
                if (!mode.equals("WALK")) {
                    transitLegs++;
                    usedTransit = true;
                }

                String encodedPoly = legNode.path("legGeometry").path("points").asText("");
                List<Coord> coords = decodePolyline(encodedPoly);
                if (coords.isEmpty()) {
                    coords = List.of(
                            new Coord(legNode.path("from").path("lat").asDouble(), legNode.path("from").path("lon").asDouble()),
                            new Coord(legNode.path("to").path("lat").asDouble(), legNode.path("to").path("lon").asDouble())
                    );
                }

                legs.add(new RouteLeg(
                        mode,
                        legNode.path("from").path("name").asText("Origin"),
                        legNode.path("to").path("name").asText("Destination"),
                        Math.round(legNode.path("duration").asDouble() / 60.0),
                        legNode.path("distance").asDouble(),
                        legNode.path("route").path("shortName").asText(""),
                        coords
                ));
            }

            double cost = transitLegs >= 2 ? 6 : (transitLegs == 1 ? 3.00 : 0.00);
            int transfers = Math.max(0, transitLegs - 1);

            results.add(new RankedItinerary(
                    buildSummary(legs),
                    totalMinutes,
                    cost,
                    transfers,
                    0.0,
                    legs
            ));
        }
        return results;
    }

    private String buildSummary(List<RouteLeg> legs) {
        return legs.stream()
                .map(l -> l.routeShortName().isEmpty() ? l.mode() : l.mode() + " " + l.routeShortName())
                .reduce((a, b) -> a + " → " + b)
                .orElse("Direct Route");
    }

    // Standard Google Encoded Polyline Decoder
    private List<Coord> decodePolyline(String encoded) {
        List<Coord> poly = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return poly;

        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new Coord(lat / 1E5, lng / 1E5));
        }
        return poly;
    }
}
