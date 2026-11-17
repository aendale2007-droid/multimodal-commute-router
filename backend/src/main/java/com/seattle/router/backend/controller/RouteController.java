package com.seattle.router.backend.controller;

import com.seattle.router.backend.dto.RankedItinerary;
import com.seattle.router.backend.dto.RouteRequest;
import com.seattle.router.backend.service.MultimodalPlannerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RouteController {

    private final MultimodalPlannerService plannerService;

    public RouteController(MultimodalPlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "service", "Seattle Multimodal Router Backend");
    }

    @PostMapping("/plan")
    public List<RankedItinerary> planRoute(@RequestBody RouteRequest request) {
        return plannerService.planAndRankRoutes(request);
    }
}
