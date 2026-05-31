package com.document.documentetl.controller;

import com.document.documentetl.dto.TokenUsageEventResponse;
import com.document.documentetl.dto.TokenUsageSummaryResponse;
import com.document.documentetl.service.TokenUsageManagerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tokens")
public class TokenUsageController {

    private final TokenUsageManagerService tokenUsageManagerService;

    public TokenUsageController(TokenUsageManagerService tokenUsageManagerService) {
        this.tokenUsageManagerService = tokenUsageManagerService;
    }

    @GetMapping("/summary")
    public TokenUsageSummaryResponse summary(@RequestParam(name = "topOperations", defaultValue = "10") int topOperations) {
        return tokenUsageManagerService.getSummary(topOperations);
    }

    @GetMapping("/events")
    public List<TokenUsageEventResponse> recentEvents(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return tokenUsageManagerService.getRecentEvents(limit);
    }

    @DeleteMapping("/events")
    public Map<String, Object> clearAllEvents() {
        long deleted = tokenUsageManagerService.clearAllEvents();
        return Map.of("deleted", deleted, "scope", "all");
    }

    @DeleteMapping("/events/before")
    public Map<String, Object> clearEventsBefore(
            @RequestParam(name = "before")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before) {
        if (before == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'before' is required.");
        }
        long deleted = tokenUsageManagerService.clearEventsBefore(before);
        return Map.of("deleted", deleted, "scope", "before", "before", before);
    }

    @GetMapping("/ui")
    public ResponseEntity<Void> tokenUi() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/token-manager.html"))
                .build();
    }
}
