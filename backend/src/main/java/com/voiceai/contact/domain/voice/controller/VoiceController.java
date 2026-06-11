package com.voiceai.contact.domain.voice.controller;

import com.voiceai.contact.domain.voice.dto.VoiceResponse;
import com.voiceai.contact.domain.voice.service.VoiceService;
import com.voiceai.contact.domain.voice.service.PerformanceMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@CrossOrigin(origins = "*") // Allows calls from any frontend port like 5173
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    private final VoiceService voiceService;
    private final PerformanceMetricsService metricsService;

    public VoiceController(VoiceService voiceService, PerformanceMetricsService metricsService) {
        this.voiceService = voiceService;
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<String> getMetrics() {
        return ResponseEntity.ok(metricsService.generateReport());
    }

    @PostMapping
    public ResponseEntity<?> handleVoice(
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            VoiceResponse response = voiceService.processVoice(audio, sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            Map<String, String> errorResponse = new HashMap<>();
            log.error("Error processing voice request", ex);
            errorResponse.put("status", "error");
            errorResponse.put("message", ex.getMessage() != null ? ex.getMessage() : ex.toString());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
