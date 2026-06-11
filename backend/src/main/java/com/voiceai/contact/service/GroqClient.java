package com.voiceai.contact.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceai.contact.dto.LlmExtractionResponse;
import com.voiceai.contact.model.SessionState;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public String classifyIntent(String transcription, SessionState state) {
        String lastAskedField = state.getLastAskedField();
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

        String systemPromptContent = "You are an intent classifier. Categorize the user's input into one of these intents:\n" +
                "- \"END\": User explicitly wants to end the conversation or say goodbye.\n" +
                "- \"SOFT_END\": User wants to pause or wait.\n" +
                "- \"ASK_QUERY\": User is asking a question about their booking or appointment details.\n" +
                "- \"CONTINUE\": User says yes, ok, go ahead, confirming the current action.\n" +
                "- \"PROVIDE_INFO\": User is providing their name, date, time, or department.\n" +
                "- \"BOOK_APPOINTMENT\": User explicitly asks to start booking an appointment.\n" +
                "- \"UNCLEAR\": Unrelated, gibberish, or cannot determine intent.\n" +
                "Output strictly a JSON object with a single key \"intent\" containing the categorized intent string.";

        if (lastAskedField != null) {
            systemPromptContent += "\nCRITICAL CONTEXT: You just explicitly asked the user for their '" + lastAskedField + "'. If they give a short answer, map it to \"PROVIDE_INFO\"!";
        }

        if (!state.getMessageHistory().isEmpty()) {
            systemPromptContent += "\nRecent Conversation History:\n";
            int start = Math.max(0, state.getMessageHistory().size() - 3);
            for (int i = start; i < state.getMessageHistory().size(); i++) {
                Map<String, String> msg = state.getMessageHistory().get(i);
                systemPromptContent += msg.get("role") + ": " + msg.get("content") + "\n";
            }
        }

        systemMsg.put("content", systemPromptContent);
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
            JsonNode intentNode = mapper.readTree(jsonOutput);
            if (intentNode.has("intent")) {
                return intentNode.get("intent").asText().toUpperCase();
            }
            return "UNCLEAR";
        } catch (Exception e) {
            System.err.println("Groq Intent Classification Error: " + e.getMessage());
            return "UNCLEAR"; 
        }
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

        String systemPromptContent = "You are a strict JSON extractor for a clinic booking voice assistant.\n" +
                "RULES:\n" +
                "1. Output ONLY a flat JSON object with exactly these 7 keys. NO nested objects. NO extra keys.\n" +
                "2. All string values must be plain strings, never objects or arrays.\n" +
                "3. If a value is not present in the input, use JSON null.\n" +
                "4. The user input may contain Bengali, Hindi, or English. ALWAYS translate names and departments into English.\n" +
                "5. Normalize department names to one of: Orthopedic, Cardiology, Neurology, Dermatology, General Physician, Pediatrics.\n" +
                "   Variants: 'हड्डी' or 'bone' or 'ortho' → Orthopedic\n" +
                "             'heart' or 'दिल' or 'cardio' → Cardiology\n" +
                "             'brain' or 'neuro' or 'दिमाग' → Neurology\n" +
                "             'skin' or 'derma' or 'त्वचा' → Dermatology\n" +
                "             'general' or 'सामान्य' → General Physician\n" +
                "             'child' or 'paed' or 'बच्चे' → Pediatrics\n" +
                "6. date: ONLY extract if the user EXPLICITLY mentions a day, date, 'आज', 'कल', 'परसों', or a specific date.\n" +
                "   If user says only 'अपॉइंटमेंट चाहिए' with NO date word → date MUST be null.\n" +
                "   When present, format as YYYY-MM-DD using the context mapping below.\n" +
                "7. time: Keep exactly as stated (e.g. '10 AM', '3 PM'). Do NOT translate or rephrase.\n" +
                "8. isConfirming: true only if user explicitly says yes/haan/confirm. false otherwise.\n" +
                "9. isQuerying: true if user asks about existing booking details or available times.\n" +
                "10. isOutOfScope: true ONLY for completely unrelated topics (weather, news). NOT for booking queries.\n\n" +
                "JSON schema:\n" +
                "{\"name\": string|null, \"department\": string|null, \"date\": string|null, \"time\": string|null, \"isConfirming\": boolean, \"isQuerying\": boolean, \"isOutOfScope\": boolean}\n\n";

        if (state.getLastAskedField() != null) {
            systemPromptContent += "CRITICAL CONTEXT: You just explicitly asked the user for their '" + state.getLastAskedField() + "'. If they give a short 1-word answer, map it to '" + state.getLastAskedField() + "' instead of marking it outOfScope! You MUST extract and translate it to Hindi/English.\n";
        }

        if (dateContext != null && !dateContext.isEmpty()) {
            systemPromptContent += "Context mapping:\n" + dateContext + "\n";
        }

        if (!state.getMessageHistory().isEmpty()) {
            systemPromptContent += "\nRecent Conversation History:\n";
            int start = Math.max(0, state.getMessageHistory().size() - 3);
            for (int i = start; i < state.getMessageHistory().size(); i++) {
                Map<String, String> msg = state.getMessageHistory().get(i);
                systemPromptContent += msg.get("role") + ": " + msg.get("content") + "\n";
            }
        }

        systemMsg.put("content", systemPromptContent);
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
            System.out.println("Extracted JSON: " + jsonOutput);
            try {
                JsonNode j = mapper.readTree(jsonOutput);
                LlmExtractionResponse r = new LlmExtractionResponse();
                if (j.hasNonNull("name") && !j.get("name").asText().equals("null")) r.setName(j.get("name").asText());
                if (j.hasNonNull("department") && !j.get("department").asText().equals("null")) r.setDepartment(j.get("department").asText());
                if (j.hasNonNull("date") && !j.get("date").asText().equals("null")) r.setDate(j.get("date").asText());
                if (j.hasNonNull("time") && !j.get("time").asText().equals("null")) r.setTime(j.get("time").asText());
                if (j.hasNonNull("isConfirming")) r.setIsConfirming(j.get("isConfirming").asBoolean(false));
                if (j.hasNonNull("isQuerying")) r.setIsQuerying(j.get("isQuerying").asBoolean(false));
                if (j.hasNonNull("isOutOfScope")) r.setIsOutOfScope(j.get("isOutOfScope").asBoolean(false));
                return r;
            } catch (Exception parseEx) {
                System.err.println("Field-level parse failed, returning empty: " + parseEx.getMessage());
                return new LlmExtractionResponse();
            }
        } catch (Exception e) {
            System.err.println("Groq JSON Extraction Error: " + e.getMessage());
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
