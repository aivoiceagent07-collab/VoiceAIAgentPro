package com.voiceai.contact.domain.voice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceai.contact.domain.voice.util.TtsCache;
import com.voiceai.contact.domain.voice.service.PerformanceMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class SarvamClient {

    private static final Logger log = LoggerFactory.getLogger(SarvamClient.class);

    @Value("${SARVAM_API_KEY:}")
    private String sarvamApiKey;

    private final RestTemplate restTemplate;
    private final TtsCache ttsCache;
    private final PerformanceMetricsService metricsService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SarvamClient(RestTemplate restTemplate, TtsCache ttsCache, PerformanceMetricsService metricsService) {
        this.restTemplate = restTemplate;
        this.ttsCache = ttsCache;
        this.metricsService = metricsService;
    }

    public String transcribeAudio(MultipartFile audio) throws Exception {
        String url = "https://api.sarvam.ai/speech-to-text";

        String contentType = audio.getContentType() != null ? audio.getContentType() : "audio/wav";
        String originalFilename = audio.getOriginalFilename();

        // Derive a safe filename with correct extension for mobile audio formats.
        // Mobile browsers often send audio/webm, audio/mp4, audio/aac without a proper filename.
        String safeFilename;
        if (originalFilename != null && !originalFilename.isBlank()) {
            safeFilename = originalFilename;
        } else {
            safeFilename = switch (contentType) {
                case "audio/webm"       -> "audio.webm";
                case "audio/mp4"        -> "audio.mp4";
                case "audio/aac"        -> "audio.aac";
                case "audio/mpeg"       -> "audio.mp3";
                case "audio/ogg"        -> "audio.ogg";
                default                 -> "audio.wav";
            };
        }

        log.info("[STT] Sending to Sarvam. ContentType={}, DerivedFilename={}, SizeBytes={}",
                contentType, safeFilename, audio.getSize());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("api-subscription-key", sarvamApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        final String finalFilename = safeFilename;
        ByteArrayResource fileAsResource = new ByteArrayResource(audio.getBytes()) {
            @Override
            public String getFilename() {
                return finalFilename;
            }
        };

        body.add("file", fileAsResource);
        body.add("model", "saaras:v3");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());

            if (root.has("transcript")) {
                String transcript = root.get("transcript").asText();
                log.info("[STT] Sarvam returned transcript field. Length={}", transcript.length());
                return transcript;
            } else if (root.has("text")) {
                String text = root.get("text").asText();
                log.info("[STT] Sarvam returned text field. Length={}", text.length());
                return text;
            } else {
                log.warn("[STT] Sarvam returned unexpected response structure: {}", root.toString());
                throw new Exception("Unexpected STT response: " + root.toString());
            }
        } catch (HttpClientErrorException e) {
            log.error("[STT] Sarvam HTTP client error. Status={}, Body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new Exception("STT Client Error: " + e.getResponseBodyAsString());
        }
    }

    public String synthesizeSpeech(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 1. Check TTS LRU Cache
        String cachedAudio = ttsCache.get(text);
        if (cachedAudio != null) {
            log.debug("Cache hit for text: '{}'", text);
            metricsService.recordCacheHit();
            return cachedAudio;
        }

        log.debug("Cache miss for text: '{}'. Requesting TTS...", text);
        metricsService.recordCacheMiss();
        String url = "https://api.sarvam.ai/text-to-speech";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-subscription-key", sarvamApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", Collections.singletonList(text));
        body.put("target_language_code", "hi-IN");
        body.put("speaker", "anushka");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            if (root.has("audios") && root.get("audios").isArray() && root.get("audios").size() > 0) {
                String base64Audio = root.get("audios").get(0).asText();
                
                // 2. Put in TTS Cache
                ttsCache.put(text, base64Audio);
                return base64Audio;
            }
            throw new Exception("No audio returned. Payload: " + response.getBody());
        } catch (HttpClientErrorException e) {
            throw new Exception("Client Error: " + e.getResponseBodyAsString());
        }
    }
}
