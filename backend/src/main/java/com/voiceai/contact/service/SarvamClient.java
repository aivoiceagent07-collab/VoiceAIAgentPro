package com.voiceai.contact.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Value("${SARVAM_API_KEY:}")
    private String sarvamApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public String transcribeAudio(MultipartFile audio) throws Exception {
        String url = "https://api.sarvam.ai/speech-to-text";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("api-subscription-key", sarvamApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource fileAsResource = new ByteArrayResource(audio.getBytes()) {
            @Override
            public String getFilename() {
                return audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.wav";
            }
        };

        body.add("file", fileAsResource);
        body.add("model", "saaras:v3");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());

            if (root.has("transcript")) {
                return root.get("transcript").asText();
            } else if (root.has("text")) {
                return root.get("text").asText();
            } else {
                return root.toString();
            }
        } catch (HttpClientErrorException e) {
            throw new Exception("Client Error: " + e.getResponseBodyAsString());
        }
    }

    public String synthesizeSpeech(String text) throws Exception {
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
                return root.get("audios").get(0).asText();
            }
            throw new Exception("No audio returned. Payload: " + response.getBody());
        } catch (HttpClientErrorException e) {
            throw new Exception("Client Error: " + e.getResponseBodyAsString());
        }
    }
}
