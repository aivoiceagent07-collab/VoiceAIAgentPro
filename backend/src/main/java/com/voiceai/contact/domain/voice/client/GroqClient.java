package com.voiceai.contact.domain.voice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceai.contact.domain.voice.dto.LlmExtractionResponse;
import com.voiceai.contact.domain.voice.model.SessionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroqClient {

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public GroqClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public LlmExtractionResponse extractGroqEntities(String transcription, String dateContext, SessionState state) {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        body.put("response_format", responseFormat);

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");

        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("You are a combined intent classifier and slot extractor for a clinic voice assistant.\n")
                .append("Output ONLY a flat JSON object with these 8 keys. Do NOT nest. Do NOT use extra keys.\n")
                .append("JSON Schema:\n")
                .append("{\n")
                .append("  \"intent\": \"END\" | \"SOFT_END\" | \"ASK_QUERY\" | \"CONTINUE\" | \"PROVIDE_INFO\" | \"BOOK_APPOINTMENT\" | \"UNCLEAR\",\n")
                .append("  \"name\": string | null,\n")
                .append("  \"department\": \"Orthopedic\" | \"Cardiology\" | \"Neurology\" | \"Dermatology\" | \"General Physician\" | \"Pediatrics\" | null,\n")
                .append("  \"date\": string (format YYYY-MM-DD) | null,\n")
                .append("  \"time\": string (as stated, e.g., '10 AM', '3 PM') | null,\n")
                .append("  \"isConfirming\": boolean,\n")
                .append("  \"isQuerying\": boolean,\n")
                .append("  \"isOutOfScope\": boolean\n")
                .append("}\n\n")
                .append("Rules:\n")
                .append("1. intent: Classify user message. 'CONTINUE' for confirmation/yes, 'PROVIDE_INFO' for slot values, 'ASK_QUERY' for timing questions, 'BOOK_APPOINTMENT' to start booking, 'END' to finish.\n")
                .append("2. name & department: Translate to English. Match department exactly to one of the 6 valid values.\n")
                .append("3. date: Extract ONLY if explicitly mentioned (e.g. today/tomorrow/monday). Map using date context. Otherwise use null.\n")
                .append("4. isConfirming: true only if user explicitly says yes/haan/confirm. isQuerying: true if asking about schedule/available doctors/times. isOutOfScope: true for unrelated requests (e.g. weather, general chats).\n");

        if (state.getLastAskedField() != null) {
            systemPromptBuilder.append("CRITICAL: You just asked the user for their '").append(state.getLastAskedField()).append("'. If they answer with a short phrase, classify intent as 'PROVIDE_INFO' and extract the slot!\n");
        }

        if (dateContext != null && !dateContext.isEmpty()) {
            systemPromptBuilder.append("Date Context:\n").append(dateContext).append("\n");
        }

        // Reduced token footprint: show structured summary instead of full raw messages
        systemPromptBuilder.append("\nSession State: ExpectedField=").append(state.getLastAskedField() != null ? state.getLastAskedField() : "None")
                .append(", Mode=").append(state.getMode())
                .append(", CurrentSlots={")
                .append("name=").append(state.getPatientName() != null ? state.getPatientName() : "null")
                .append(", dept=").append(state.getDepartment() != null ? state.getDepartment() : "null")
                .append(", date=").append(state.getDate() != null ? state.getDate() : "null")
                .append(", time=").append(state.getTime() != null ? state.getTime() : "null")
                .append("}\n");

        systemMsg.put("content", systemPromptBuilder.toString());
        messages.add(systemMsg);

        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        userMsgMap.put("content", transcription);
        messages.add(userMsgMap);

        body.put("messages", messages);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            String jsonOutput = root.path("choices").path(0).path("message").path("content").asText();
            System.out.println("[GroqClient] Extracted JSON: " + jsonOutput);
            try {
                JsonNode j = mapper.readTree(jsonOutput);
                LlmExtractionResponse r = new LlmExtractionResponse();
                if (j.hasNonNull("intent")) r.setIntent(j.get("intent").asText().toUpperCase());
                if (j.hasNonNull("name") && !j.get("name").asText().equals("null")) r.setName(j.get("name").asText());
                if (j.hasNonNull("department") && !j.get("department").asText().equals("null")) r.setDepartment(j.get("department").asText());
                if (j.hasNonNull("date") && !j.get("date").asText().equals("null")) r.setDate(j.get("date").asText());
                if (j.hasNonNull("time") && !j.get("time").asText().equals("null")) r.setTime(j.get("time").asText());
                if (j.hasNonNull("isConfirming")) r.setIsConfirming(j.get("isConfirming").asBoolean(false));
                if (j.hasNonNull("isQuerying")) r.setIsQuerying(j.get("isQuerying").asBoolean(false));
                if (j.hasNonNull("isOutOfScope")) r.setIsOutOfScope(j.get("isOutOfScope").asBoolean(false));
                
                // Keep default fallback if intent is null or missing
                if (r.getIntent() == null) {
                    r.setIntent("UNCLEAR");
                }
                return r;
            } catch (Exception parseEx) {
                System.err.println("[GroqClient] Field-level parse failed, returning empty: " + parseEx.getMessage());
                return new LlmExtractionResponse();
            }
        } catch (Exception e) {
            System.err.println("[GroqClient] Groq combined Extraction Error: " + e.getMessage());
            return new LlmExtractionResponse();
        }
    }

    public String generateGroqResponse(String contextStr) throws Exception {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        String sysPrompt =
            "You are a strict response formatter for a clinic receptionist voice assistant.\n" +
            "You are NOT an AI. You do NOT think. You ONLY echo the GIVEN CONTEXT into ONE Hindi sentence.\n\n" +

            "=== ABSOLUTE RULES ===\n" +
            "1. NEVER change the department name. Use it EXACTLY as given (e.g. Orthopedic stays 'Orthopedic', DO NOT translate).\n" +
            "2. NEVER translate department names to Sanskrit/Hindi medical terms (e.g. Orthopedic ≠ वृक्कशास्त्र).\n" +
            "3. NEVER change time values. Convert 24h to 12h ONLY: 13:00 → 1 बजे, 10:00 → 10 बजे. Never say '13 बजे'.\n" +
            "4. NEVER add patient name prefix like 'राहुल जी'. Start directly with the doctor or time.\n" +
            "5. NEVER add words: बीमारी, चिकित्सा, विशेषज्ञ, समस्या, or any word NOT in CONTEXT.\n" +
            "6. NEVER infer day/date. Use EXACTLY what is in CONTEXT.\n" +
            "7. Return ONLY one sentence. No explanation.\n\n" +

            "=== LANGUAGE ===\n" +
            "- Hindi (Devanagari only).\n" +
            "- Short, polite receptionist style.\n" +
            "- Max 12 words.\n\n" +

            "=== NEXT ACTION RULES ===\n" +
            "INFORM      → 'रविवार को डॉ आयर 10 से 1 तक उपलब्ध हैं।' (day+doctor+start से end तक only)\n" +
            "CONFIRM_DETAILS → 'आपका अपॉइंटमेंट [day] को [time] बजे [doctor] के साथ है, क्या कन्फर्म करूँ?' (use EXACT values only)\n" +
            "OUTPUT FORMAT: Return ONLY the final Hindi sentence. Nothing else.";
        systemMsg.put("content", sysPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", contextStr);
        messages.add(userMsg);

        body.put("messages", messages);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").path(0).path("message").path("content").asText().trim();
        } catch (Exception e) {
            throw new Exception("Groq NLG Error: " + e.getMessage());
        }
    }
}
