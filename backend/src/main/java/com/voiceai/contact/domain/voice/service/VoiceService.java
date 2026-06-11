package com.voiceai.contact.domain.voice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.voiceai.contact.domain.voice.dto.VoiceResponse;
import com.voiceai.contact.domain.voice.dto.LlmExtractionResponse;
import com.voiceai.contact.domain.voice.model.SessionState;
import com.voiceai.contact.domain.voice.client.SarvamClient;
import com.voiceai.contact.domain.voice.client.GroqClient;
import com.voiceai.contact.domain.voice.util.SpeechFormatter;

import java.util.*;

@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    @Value("${SARVAM_API_KEY:}")
    private String sarvamApiKey;

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    private final DateNormalizerService dateNormalizerService;
    private final SessionManagerService sessionManagerService;
    private final InputValidatorService inputValidatorService;

    // Modular Clients/Services
    private final SarvamClient sarvamClient;
    private final GroqClient groqClient;
    private final ClinicService clinicService;
    private final SpeechFormatter speechFormatter;
    private final DeterministicExtractionService deterministicExtractionService;
    private final DepartmentRoutingService departmentRoutingService;
    private final PerformanceMetricsService metricsService;
    private final DepartmentNormalizationService departmentNormalizationService;

    public VoiceService(DateNormalizerService dateNormalizerService,
            SessionManagerService sessionManagerService,
            InputValidatorService inputValidatorService,
            SarvamClient sarvamClient,
            GroqClient groqClient,
            ClinicService clinicService,
            SpeechFormatter speechFormatter,
            DeterministicExtractionService deterministicExtractionService,
            DepartmentRoutingService departmentRoutingService,
            PerformanceMetricsService metricsService,
            DepartmentNormalizationService departmentNormalizationService) {
        this.dateNormalizerService = dateNormalizerService;
        this.sessionManagerService = sessionManagerService;
        this.inputValidatorService = inputValidatorService;
        this.sarvamClient = sarvamClient;
        this.groqClient = groqClient;
        this.clinicService = clinicService;
        this.speechFormatter = speechFormatter;
        this.deterministicExtractionService = deterministicExtractionService;
        this.departmentRoutingService = departmentRoutingService;
        this.metricsService = metricsService;
        this.departmentNormalizationService = departmentNormalizationService;
    }

    public VoiceResponse processVoice(MultipartFile audio, String sessionId) throws Exception {
        long startReqTime = System.currentTimeMillis();
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        if (sarvamApiKey == null || sarvamApiKey.isEmpty() || groqApiKey == null || groqApiKey.isEmpty()) {
            throw new Exception("API keys are missing in the environment configuration.");
        }

        // 1. Initial Greeting when audio is missing/null (session initialization)
        if (audio == null || audio.isEmpty()) {
            if (!state.isGreetingDone()) {
                state.setGreetingDone(true);
                String greetingWord = getGreetingWord();
                String greetMsg;
                if (state.getPatientName() != null && !state.getPatientName().trim().isEmpty()) {
                    greetMsg = greetingWord + " " + formatPersonalizedName(state.getPatientName()) + "। मैं आपकी क्या मदद कर सकती हूँ?";
                } else {
                    greetMsg = greetingWord + "। मैं क्लिनिक की रिसेप्शनिस्ट हूँ। मैं आपकी क्या मदद कर सकती हूँ?";
                }
                state.appendMessage("assistant", greetMsg);
                log.debug("Initial greeting sent.");
                long startTts = System.currentTimeMillis();
                String audioBase64 = sarvamClient.synthesizeSpeech(greetMsg);
                metricsService.recordTts(System.currentTimeMillis() - startTts);
                metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
                log.info("[METRICS] Processed initial greeting. Latency={} ms", System.currentTimeMillis() - startReqTime);
                return new VoiceResponse("", greetMsg, audioBase64, false,
                        state.getSessionId());
            } else {
                metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
                log.info("[METRICS] Processed empty greeting. Latency={} ms", System.currentTimeMillis() - startReqTime);
                return new VoiceResponse("", "", "", false, state.getSessionId());
            }
        }

        // 2. STT (Speech to Text) via Sarvam
        log.debug("Transcribing audio...");
        String transcription;
        long startStt = System.currentTimeMillis();
        try {
            transcription = sarvamClient.transcribeAudio(audio);
            metricsService.recordStt(System.currentTimeMillis() - startStt);
            log.debug("Transcription: {}", transcription);
        } catch (Exception e) {
            log.error("Failed STT: {}", e.getMessage());
            metricsService.recordStt(System.currentTimeMillis() - startStt);
            transcription = "नमस्ते, यह एक टेस्ट है।"; // Fallback text
        }

        if (transcription == null || transcription.trim().isEmpty()) {
            log.debug("No speech detected. Returning fallback audio directly and skipping LLM.");
            String fallbackMsg = "मुझे आपकी आवाज़ सुनाई नहीं दी। कृपया फिर से प्रयास करें।";
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(fallbackMsg);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            log.info("[METRICS] Processed empty transcription fallback. Latency={} ms", System.currentTimeMillis() - startReqTime);
            return new VoiceResponse("", fallbackMsg, audioBase64, false, state.getSessionId());
        }

        // Script normalization (Bengali → Devanagari/Canonical terms)
        transcription = transcription
                .replace("অর্থোপেডিক", "Orthopedic")
                .replace("অস্থি", "Orthopedic")
                .replace("ডাক্তার", "Doctor")
                .replace("অ্যাপয়েন্টমেন্ট", "Appointment")
                .replace("রাহুল", "राहुल")
                .replace("काल", "कल")
                .replace("आज", "आज");
        log.debug("Normalized input: {}", transcription);

        // Input validation
        if (!inputValidatorService.isValidInput(transcription, state.getLastAskedField())) {
            log.debug("Input rejected by validator: {}", transcription);
            String errorMsg = "माफ़ कीजिए, मुझे समझ नहीं आया। क्या आप फिर से बता सकते हैं?";
            state.appendMessage("assistant", errorMsg);
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(errorMsg);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            log.info("[METRICS] Processed validation rejection. Latency={} ms", System.currentTimeMillis() - startReqTime);
            return new VoiceResponse(transcription, errorMsg, audioBase64, false,
                    state.getSessionId());
        }

        // Add user text to memory
        state.appendMessage("user", transcription);

        // Handle explicit department selection FIRST
        String explicitDept = getExplicitDepartment(transcription);
        if (explicitDept != null) {
            state.setDepartment(explicitDept);
            state.setSuggestedDepartment(null);
            if ("department_suggestion".equals(state.getLastAskedField()) || "department".equals(state.getLastAskedField())) {
                state.setLastAskedField(null);
            }
            log.info("[ROUTING EXPLICIT] Final Department={} set directly.", explicitDept);
        }

        // Handle suggestion confirmation loops
        if (state.getDepartment() == null && "department_suggestion".equals(state.getLastAskedField())) {
            String cleanInput = transcription.toLowerCase().trim();
            boolean hasNegation = cleanInput.contains("नहीं") || cleanInput.contains("मत") || cleanInput.contains("ना ") || cleanInput.contains("no");
            boolean isConfirm = !hasNegation && (cleanInput.contains("हाँ") || cleanInput.contains("हां") || cleanInput.contains("yes") ||
                    cleanInput.contains("कर दीजिए") || cleanInput.contains("कर दो") || cleanInput.contains("बिल्कुल") ||
                    cleanInput.contains("कन्फर्म") || cleanInput.contains("ठीक है") || cleanInput.contains("जी") || cleanInput.contains("sure"));
            
            if (isConfirm) {
                state.setDepartment(state.getSuggestedDepartment());
                state.setSuggestedDepartment(null);
                state.setLastAskedField(null);
                log.info("[ROUTING CONFIRMED] Department={} confirmed by user.", state.getDepartment());
            } else if (hasNegation) {
                // Check if they supplied a different explicit department choice (e.g. "नहीं, कार्डियोलॉजी")
                if (explicitDept != null) {
                    state.setDepartment(explicitDept);
                    state.setSuggestedDepartment(null);
                    state.setLastAskedField(null);
                } else {
                    state.setSuggestedDepartment(null);
                    state.setLastAskedField("department");
                    log.info("[ROUTING NEGATED] Suggested department rejected. Asking directly.");
                }
            } else {
                // If neither explicit confirm nor negate, check if they specified another department directly anyway
                if (explicitDept != null) {
                    state.setDepartment(explicitDept);
                    state.setSuggestedDepartment(null);
                    state.setLastAskedField(null);
                } else {
                    // Invalid/unrelated response - do NOT modify state (do not clear suggested department or expected field)
                    log.info("[ROUTING INVALID/UNRELATED] Unrelated input during suggestion. Keeping state unchanged.");
                }
            }
        }

        // 3. Deterministic Extraction Layer (Pre-LLM) & Symptom Department Routing
        DeterministicExtractionService.ExtractionResult detResult = deterministicExtractionService.extract(transcription, state);
        
        // Suggest department (if department is null and no suggested department is yet active)
        String currentExpected = getNextExpectedField(state);
        if ("department".equals(currentExpected) && state.getDepartment() == null && state.getSuggestedDepartment() == null) {
            DepartmentRoutingService.DepartmentRoutingResult routingResult = departmentRoutingService.routeWithDetails(transcription);
            if (routingResult != null) {
                state.setSuggestedDepartment(routingResult.getDepartment());
                state.setSuggestedDeptConfidence(routingResult.getConfidence());
                log.info("[ROUTING SUGGESTION] Suggested={}, Source={}, Confidence={}", 
                         routingResult.getDepartment(), routingResult.getLayer(), routingResult.getConfidence());
                
                String sourceKey = "llm";
                if (routingResult.getLayer().startsWith("Exact") || routingResult.getLayer().startsWith("Keyword")) {
                    sourceKey = "dictionary";
                } else if (routingResult.getLayer().startsWith("Semantic")) {
                    sourceKey = "similarity";
                }
                metricsService.recordRouting(sourceKey);
            }
        }

        LlmExtractionResponse extracted;
        String intent;
        if (detResult.isSuccess()) {
            log.debug("Deterministic extraction bypass triggered. ExpectedField={}", state.getLastAskedField());
            extracted = new LlmExtractionResponse();
            extracted.setIntent(detResult.getIntent());
            extracted.setName(detResult.getName());
            extracted.setDepartment(detResult.getDepartment() != null ? detResult.getDepartment() : state.getDepartment());
            extracted.setDate(detResult.getDate());
            extracted.setTime(detResult.getTime() != null ? detResult.getTime() : null);
            extracted.setIsConfirming(detResult.getIsConfirming());
            extracted.setIsQuerying(detResult.getIsQuerying());
            extracted.setIsOutOfScope(detResult.getIsOutOfScope());
            intent = detResult.getIntent();
        } else {
            long startLlm = System.currentTimeMillis();
            String dateContext = dateNormalizerService.getDateContext(transcription);
            log.debug("Extracting AI entities for: {}", transcription);
            extracted = groqClient.extractGroqEntities(transcription, dateContext, state);
            metricsService.recordLlm(System.currentTimeMillis() - startLlm);
            metricsService.recordRouting("llm");
            intent = extracted.getIntent();
        }

        // Hard Override for Intents (deterministic keyword rules override LLM)
        String cTextCheck = transcription.toLowerCase().trim();

        // Confirmation override — MUST happen before SOFT_END/END check
        boolean hasNegation = cTextCheck.contains("नहीं") || cTextCheck.contains("मत") || cTextCheck.contains("ना ");
        boolean isConfirmKeyword = !hasNegation && (cTextCheck.contains("हाँ") || cTextCheck.contains("हां")
                || cTextCheck.contains("yes") ||
                cTextCheck.contains("कर दीजिए") || cTextCheck.contains("कर दो") || cTextCheck.contains("बिल्कुल") ||
                cTextCheck.contains("कन्फर्म") || cTextCheck.contains("ठीक है") || cTextCheck.contains("जी")
                || cTextCheck.contains("sure"));

        boolean isNegativeConfirm = hasNegation && state.getMode() == SessionState.Mode.CONFIRMATION
                && (cTextCheck.contains("नहीं") || cTextCheck.contains("ठीक नहीं")
                        || cTextCheck.contains("कन्फर्म नहीं"));

        if (isConfirmKeyword && state.getMode() == SessionState.Mode.CONFIRMATION) {
            intent = "CONTINUE";
        } else if (cTextCheck.equals("नहीं") || cTextCheck.equals("बस") || cTextCheck.equals("no") ||
                cTextCheck.contains("धन्यवाद") || cTextCheck.contains("ज़रूरत नहीं") ||
                cTextCheck.contains("thank you") || cTextCheck.contains("thanks")
                || cTextCheck.contains("डिस्कनेक्ट")) {
            if (state.getLastAskedField() == null || state.getMode() != SessionState.Mode.BOOKING) {
                intent = "END";
            }
        } else if (cTextCheck.contains("कब है") || cTextCheck.contains("टाइम क्या है") ||
                cTextCheck.contains("समय क्या है") || cTextCheck.contains("कौन सा टाइम") ||
                cTextCheck.contains("कौन सा समय") || cTextCheck.contains("कब मिलेंगे") ||
                cTextCheck.contains("कब उपलब्ध") || cTextCheck.contains("available") ||
                cTextCheck.contains("अवेलेबल") || cTextCheck.contains("उपलब्ध") ||
                cTextCheck.contains("कब आएं") || cTextCheck.contains("टाइम बताइए")) {
            intent = "ASK_QUERY";
        } else if (cTextCheck.contains("appointment") || cTextCheck.contains("अपॉइंटमेंट")) {
            if (!intent.equals("CANCEL") && state.getMode() == SessionState.Mode.BOOKING
                    && state.getPatientName() == null) {
                intent = "BOOK_APPOINTMENT";
            }
        }

        if (intent.equals("CANCEL") && !cTextCheck.contains("कैंसिल") && !cTextCheck.contains("cancel")) {
            intent = "UNCLEAR";
        }

        // RESCHEDULE override
        boolean isRescheduleKeyword = cTextCheck.contains("रीशेडिउल") || cTextCheck.contains("रिशेडिउल")
                || cTextCheck.contains("रीशिडिउल") || cTextCheck.contains("reschedule")
                || cTextCheck.contains("दूसरे दिन") || cTextCheck.contains("दूसरी तारीख")
                || cTextCheck.contains("दिन बदलना") || cTextCheck.contains("बदलना है");
        if (isRescheduleKeyword) {
            intent = "RESCHEDULE";
        }

        log.debug("Classified Intent: {}", intent);

        // Failsafe Priority Order
        if (state.getLastAskedField() != null) {
            if (!intent.equals("END") && !intent.equals("CONTINUE") && !intent.equals("ASK_QUERY")
                    && !intent.equals("CANCEL") && !intent.equals("RESCHEDULE")) {
                intent = "PROVIDE_INFO";
            }
        }

        // RESCHEDULE
        if (intent.equals("RESCHEDULE") && (state.isConfirmed() || state.getMode() == SessionState.Mode.POST_CONFIRM)) {
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setMode(SessionState.Mode.RESCHEDULE);
            state.setLastAskedField("date");
            state.resetRepeatCount();
            String reschMsg = "ठीक है " + formatPersonalizedName(state.getPatientName()) + ", आप किस दिन अपॉइंटमेंट रखना चाहेंगे?";
            state.appendMessage("assistant", reschMsg);
            log.debug("RESCHEDULE triggered from POST_CONFIRM");
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(reschMsg);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            log.info("[METRICS] Processed reschedule request. Latency={} ms", System.currentTimeMillis() - startReqTime);
            return new VoiceResponse(transcription, reschMsg, audioBase64, false,
                    state.getSessionId());
        }

        // Hard END
        boolean isSlotElicitation = "name".equals(state.getLastAskedField())
                || "department".equals(state.getLastAskedField())
                || "department_suggestion".equals(state.getLastAskedField())
                || "date".equals(state.getLastAskedField())
                || "time".equals(state.getLastAskedField());
        boolean hardEnd = intent.equals("END")
                || ((cTextCheck.equals("नहीं") || cTextCheck.equals("नहीं।")
                     || cTextCheck.equals("बस") || cTextCheck.equals("बस।")
                     || cTextCheck.equals("no")) && !isSlotElicitation)
                || cTextCheck.contains("धन्यवाद")
                || cTextCheck.contains("ठीक है, बस")
                || cTextCheck.contains("thank you") || cTextCheck.contains("thanks")
                || cTextCheck.contains("डिस्कनेक्ट");

        if (hardEnd && state.getMode() != SessionState.Mode.CONFIRMATION) {
            String aiResponse = "धन्यवाद। आपका दिन शुभ हो।";
            state.appendMessage("assistant", aiResponse);
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(aiResponse);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            log.info("[METRICS] Processed hard-end request. Latency={} ms", System.currentTimeMillis() - startReqTime);
            return new VoiceResponse(transcription, aiResponse, audioBase64, true,
                    state.getSessionId());
        }

        // POST_CONFIRM
        if (state.isConfirmed() && state.getMode() == SessionState.Mode.POST_CONFIRM) {
            String answer = speechFormatter.resolvePostConfirmQuery(cTextCheck, state);
            if (answer != null) {
                state.appendMessage("assistant", answer);
                long startTts = System.currentTimeMillis();
                String audioBase64 = sarvamClient.synthesizeSpeech(answer);
                metricsService.recordTts(System.currentTimeMillis() - startTts);
                metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
                log.info("[METRICS] Processed post-confirm query. Latency={} ms", System.currentTimeMillis() - startReqTime);
                return new VoiceResponse(transcription, answer, audioBase64, false,
                        state.getSessionId());
            }
        }

        // Confirmed general booking query
        if (intent.equals("ASK_QUERY") && state.isConfirmed()) {
            String dayHindi = speechFormatter.toHindiDay(speechFormatter.getDayOfWeek(state.getDate()));
            String timeNatural = speechFormatter.toNaturalTime(state.getTime());
            String docName = speechFormatter.formatDoctorName(state.getAssignedDoctor());
            String aiResponse = "आपका अपॉइंटमेंट " + dayHindi + " को " + timeNatural + " " + docName + " के साथ है।";
            state.setLastAskedField(null);
            state.appendMessage("assistant", aiResponse);
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(aiResponse);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            log.info("[METRICS] Processed confirmed query. Latency={} ms", System.currentTimeMillis() - startReqTime);
            return new VoiceResponse(transcription, aiResponse, audioBase64, false,
                    state.getSessionId());
        }

        int wordCount = transcription.split("\\s+").length;
        if (wordCount <= 3 && state.getLastAskedField() != null) {
            extracted.setIsOutOfScope(false);
        }

        if (state.getDepartment() != null || state.getSuggestedDepartment() != null) {
            extracted.setIsOutOfScope(false);
        }

        // Hard Out-of-Scope gate
        if (extracted.getIsOutOfScope() != null && extracted.getIsOutOfScope()
                && (extracted.getIsQuerying() == null || !extracted.getIsQuerying())) {
            String oosMsg = "माफ़ कीजिए, मैं सिर्फ अपॉइंटमेंट बुकिंग में आपकी मदद कर सकती हूँ।";
            state.appendMessage("assistant", oosMsg);
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(oosMsg);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            log.info("[METRICS] Processed Out-Of-Scope request. Latency={} ms", System.currentTimeMillis() - startReqTime);
            return new VoiceResponse(transcription, oosMsg, audioBase64, false,
                    state.getSessionId());
        }

        log.debug("Extracted: name={}, dept={}, date={}, time={}", extracted.getName(),
                extracted.getDepartment(), extracted.getDate(), extracted.getTime());

        boolean timeInvalid = false;
        boolean availabilityConflict = false;
        String systemData = "";
        String nextAction = "";
        boolean endCall = false;

        // Safe Entity Hydration
        if (!state.isConfirmed() && state.getMode() != SessionState.Mode.CONFIRMATION) {
            String cleanText = transcription.replaceAll("[।.,!?\\s]+", "").trim();

            if (state.getRepeatCount() >= 2 && state.getLastAskedField() != null) {
                log.debug("Loop detected (repeat={}), force-accepting for: {}", state.getRepeatCount(),
                        state.getLastAskedField());
                forceAcceptForField(state, cleanText, transcription, extracted);
                state.resetRepeatCount();
            } else {
                // Sequential hydration based on the expected field first
                String expectedField = state.getLastAskedField();
                if ("name".equals(expectedField)) {
                    String val = extracted.getName();
                    if (isValidValue(val) && isValidName(val)) {
                        state.setPatientName(val);
                    } else if (isValidName(cleanText)) {
                        state.setPatientName(cleanText);
                        log.debug("Name via heuristic fallback: {}", cleanText);
                    }
                } else if ("department".equals(expectedField)) {
                    String val = normalizeDepartment(extracted.getDepartment() != null ? extracted.getDepartment() : explicitDept);
                    if (isValidValue(val)) {
                        state.setDepartment(val);
                    } else {
                        String fallback = normalizeDepartment(cleanText);
                        if (isValidValue(fallback)) {
                            state.setDepartment(fallback);
                        }
                    }
                } else if ("date".equals(expectedField)) {
                    String weekdayDate = speechFormatter.resolveWeekdayFromText(transcription);
                    String val = (weekdayDate != null) ? weekdayDate : extracted.getDate();
                    log.debug("Date candidate: weekday={}, llm={}, using={}", weekdayDate, extracted.getDate(), val);
                    if (isValidValue(val) && val.contains(",")) {
                        systemData = "MULTI_DATE";
                    } else if (isValidValue(val)) {
                        if (isPastDate(val)) {
                            systemData = "PAST_DATE";
                            state.setDate(null);
                        } else {
                            state.setDate(val);
                        }
                    }
                } else if ("time".equals(expectedField)) {
                    String candidateTime = isValidValue(extracted.getTime()) ? extracted.getTime() : null;
                    if (candidateTime == null && !transcription.replaceAll("[^0-9]", "").isEmpty()) {
                        candidateTime = transcription.trim();
                    }
                    if (candidateTime != null) {
                        java.time.LocalTime parsedTime = speechFormatter.parseTimeToLocalTime(candidateTime);
                        if (parsedTime != null) {
                            if (clinicService.isTimeInSlot(parsedTime, state)) {
                                state.setTime(parsedTime);
                            } else {
                                timeInvalid = true;
                                availabilityConflict = true;
                                state.setTime(null);
                            }
                        } else {
                            timeInvalid = true;
                            availabilityConflict = true;
                            state.setTime(null);
                        }
                    }
                }

                // General hydration fallback for other fields if not already set
                if (!"PAST_DATE".equals(systemData)) {
                    // Name fallback
                    String eName = extracted.getName();
                    if (isValidValue(eName) && isValidName(eName) && state.getPatientName() == null) {
                        state.setPatientName(eName);
                    }
                    // Department fallback
                    if (!"department_suggestion".equals(state.getLastAskedField())) {
                        String eDept = normalizeDepartment(extracted.getDepartment() != null ? extracted.getDepartment() : explicitDept);
                        if (isValidValue(eDept) && state.getDepartment() == null) {
                            state.setDepartment(eDept);
                        }
                    }
                    // Date fallback
                    String _weekdayDate = speechFormatter.resolveWeekdayFromText(transcription);
                    String eDate = (_weekdayDate != null) ? _weekdayDate : extracted.getDate();
                    if (isValidValue(eDate) && (state.getDate() == null || state.getMode() == SessionState.Mode.RESCHEDULE)) {
                        if (isPastDate(eDate)) {
                            systemData = "PAST_DATE";
                            state.setDate(null);
                        } else {
                            state.setDate(eDate);
                        }
                    }
                    // Time fallback
                    String eTime = extracted.getTime();
                    if (isValidValue(eTime) && state.getTime() == null) {
                        java.time.LocalTime parsedTime = speechFormatter.parseTimeToLocalTime(eTime);
                        if (parsedTime != null) {
                            if (clinicService.isTimeInSlot(parsedTime, state)) {
                                state.setTime(parsedTime);
                            } else {
                                timeInvalid = true;
                                availabilityConflict = true;
                                state.setTime(null);
                            }
                        }
                    }
                }
            }
        }

        boolean justEnteredConfirmation = false;
        if (areAllSlotsValid(state) && !state.isConfirmed()) {
            if (state.getMode() == SessionState.Mode.BOOKING || state.getMode() == SessionState.Mode.RESCHEDULE) {
                state.setMode(SessionState.Mode.CONFIRMATION);
                justEnteredConfirmation = true;
                if (state.getAssignedDoctor() == null) {
                    state.setAssignedDoctor(clinicService.matchDoctorRaw(state));
                }
            }
        } else {
            if (state.getMode() == SessionState.Mode.CONFIRMATION) {
                state.setMode(SessionState.Mode.BOOKING);
                state.setConfirmed(false);
                log.info("[STATE CORRECTION] Mode demoted to BOOKING because slots are not all valid.");
            }
        }

        boolean isCancel = intent.equals("CANCEL") || cTextCheck.contains("कैंसिल") || cTextCheck.contains("cancel");
        boolean isEnd = intent.equals("END") || cTextCheck.equals("नहीं") || cTextCheck.equals("बस")
                || cTextCheck.contains("धन्यवाद") || cTextCheck.contains("डिस्कनेक्ट");
        boolean isReschedule = intent.equals("RESCHEDULE")
                || cTextCheck.contains("रीशेड्यूल") || cTextCheck.contains("रिशेड्यूल")
                || cTextCheck.contains("रीशिड्यूल") || cTextCheck.contains("reschedule")
                || cTextCheck.contains("दूसरे दिन") || cTextCheck.contains("दिन बदलना")
                || cTextCheck.contains("बदलना है");
        boolean isPositive = !justEnteredConfirmation && (isConfirmKeyword
                || (extracted.getIsConfirming() != null && extracted.getIsConfirming()) || intent.equals("CONTINUE"));

        if ("MULTI_DATE".equals(systemData)) {
            nextAction = "ASK_DATE";
            state.setLastAskedField("date");
            systemData = "";
        } else if ("PAST_DATE".equals(systemData)) {
            nextAction = "ASK_DATE";
            state.setLastAskedField("date");
            systemData = "यह तारीख़ बीत चुकी है। कृपया भविष्य की तारीख़ बताएं।";
        } else if (isEnd && state.getMode() != SessionState.Mode.CONFIRMATION) {
            nextAction = "END";
            endCall = true;
            state.setMode(SessionState.Mode.POST_CONFIRM);
        } else if (isReschedule && (state.isConfirmed() || state.getMode() == SessionState.Mode.POST_CONFIRM
                || state.getMode() == SessionState.Mode.CONFIRMATION)) {
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setMode(SessionState.Mode.RESCHEDULE);
            state.setLastAskedField("date");
            nextAction = "ASK_DATE";
            systemData = "ठीक है, नई तारीख़ बताइए।";
        } else if (isCancel) {
            state.setPatientName(null);
            state.setDepartment(null);
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setMode(SessionState.Mode.BOOKING);
            state.setLastAskedField(null);
            nextAction = "CANCEL";
        } else if (intent.equals("SOFT_END")) {
            if (state.getMode() == SessionState.Mode.CONFIRMATION) {
                nextAction = "CONFIRM_DETAILS";
                systemData = buildBookingSummary(state);
            } else {
                nextAction = "END";
                endCall = true;
            }
        } else if (intent.equals("UNCLEAR") && !justEnteredConfirmation) {
            if (state.getLastAskedField() != null) {
                nextAction = "ASK_" + state.getLastAskedField().toUpperCase();
            } else {
                nextAction = "INFORM";
                systemData = "माफ़ कीजिए, मुझे यह स्पष्ट नहीं समझ आया। कृपया फिर से बताएं।";
            }
        } else if ((intent.equals("ASK_QUERY") || (extracted.getIsQuerying() != null && extracted.getIsQuerying()))
                && !justEnteredConfirmation) {
            nextAction = "INFORM";
            if (state.isConfirmed() || state.getMode() == SessionState.Mode.CONFIRMATION) {
                String dH = speechFormatter.toHindiDay(speechFormatter.getDayOfWeek(state.getDate()));
                String tN = speechFormatter.toNaturalTime(state.getTime());
                String dN = speechFormatter.formatDoctorName(state.getAssignedDoctor());
                systemData = "आपका अपॉइंटमेंट " + dH + " को " + tN + " " + dN + " के साथ है।";
            } else {
                if (state.getMode() == SessionState.Mode.RESCHEDULE) {
                    String _wd = speechFormatter.resolveWeekdayFromText(transcription);
                    if (_wd != null && !_wd.equals(state.getDate())) {
                        log.debug("RESCHEDULE: date updated from query: {} → {}", state.getDate(), _wd);
                        state.setDate(_wd);
                    } else if (isValidValue(extracted.getDate()) && !extracted.getDate().contains(",")) {
                        String llmD = extracted.getDate();
                        if (!llmD.equals(state.getDate()) && !isPastDate(llmD)) {
                            log.debug("RESCHEDULE: date updated from LLM query: {} → {}", state.getDate(), llmD);
                            state.setDate(llmD);
                        }
                    }
                }
                systemData = clinicService.buildAvailabilityResponse(state);
            }
        } else if (state.getMode() == SessionState.Mode.BOOKING || state.getMode() == SessionState.Mode.RESCHEDULE) {
            currentExpected = getNextExpectedField(state);
            if ("name".equals(currentExpected)) {
                nextAction = "ASK_NAME";
                state.setLastAskedField("name");
                state.incrementRepeatCount();
            } else if ("department".equals(currentExpected)) {
                if (state.getSuggestedDepartment() != null) {
                    double confidence = state.getSuggestedDeptConfidence();
                    if (confidence >= 0.95) {
                        nextAction = "CONFIRM_DEPARTMENT_SUGGESTION_HIGH";
                        state.setLastAskedField("department_suggestion");
                    } else if (confidence >= 0.80) {
                        nextAction = "CONFIRM_DEPARTMENT_SUGGESTION_MEDIUM";
                        state.setLastAskedField("department_suggestion");
                    } else {
                        // Low confidence, ask directly
                        nextAction = "ASK_DEPARTMENT";
                        state.setLastAskedField("department");
                        state.setSuggestedDepartment(null); // Clear suggested
                    }
                } else {
                    nextAction = "ASK_DEPARTMENT";
                    state.setLastAskedField("department");
                    state.incrementRepeatCount();
                }
            } else if ("date".equals(currentExpected)) {
                nextAction = "ASK_DATE";
                state.setLastAskedField("date");
                state.incrementRepeatCount();
            } else if ("time".equals(currentExpected)) {
                nextAction = "ASK_TIME";
                state.setLastAskedField("time");
                state.incrementRepeatCount();
            }
        } else if (state.getMode() == SessionState.Mode.CONFIRMATION) {
            if (isNegativeConfirm) {
                state.setTime(null);
                state.setAssignedDoctor(null);
                state.setMode(SessionState.Mode.BOOKING);
                state.setLastAskedField("time");
                state.resetRepeatCount();
                nextAction = "NEG_CONFIRM";
            } else if (isPositive) {
                state.setConfirmed(true);
                state.setMode(SessionState.Mode.POST_CONFIRM);
                state.setLastAskedField(null);
                nextAction = "POST_CONFIRM";
            } else {
                nextAction = "CONFIRM_DETAILS";
                state.setLastAskedField("confirmation");
                systemData = buildBookingSummary(state);
            }
        } else if (state.getMode() == SessionState.Mode.POST_CONFIRM) {
            nextAction = "POST_CONFIRM";
        }

        String contextStr = buildLLMContext(state, nextAction, systemData);
        log.debug("Generated LLM Context:\n{}", contextStr);

        String aiResponse;
        switch (nextAction) {
            case "ASK_NAME" -> aiResponse = "कृपया अपना नाम बताइए।";
            case "ASK_DEPARTMENT" -> aiResponse = state.getRepeatCount() <= 1
                    ? "किस विभाग में दिखाना है?"
                    : "कृपया बताएं आपको किस तरह के डॉक्टर को दिखाना है, जैसे हड्डी, दिल, नसें या त्वचा।";
            case "CONFIRM_DEPARTMENT_SUGGESTION_HIGH" -> {
                String deptHi = speechFormatter.formatDeptName(state.getSuggestedDepartment());
                aiResponse = "आपको " + deptHi + " विभाग में दिखाना उचित रहेगा। क्या मैं यही विभाग चुनूँ?";
            }
            case "CONFIRM_DEPARTMENT_SUGGESTION_MEDIUM" -> {
                String deptHi = speechFormatter.formatDeptName(state.getSuggestedDepartment());
                aiResponse = "आपको " + deptHi + " या किसी अन्य विभाग में दिखाना है?";
            }
            case "ASK_DATE" -> {
                if (state.getMode() == SessionState.Mode.RESCHEDULE && state.getRepeatCount() <= 1) {
                    aiResponse = "ठीक है " + formatPersonalizedName(state.getPatientName()) + ", आप किस दिन अपॉइंटमेंट रखना चाहेंगे?";
                } else {
                    String basePrompt = state.getRepeatCount() <= 1
                            ? "किस दिन आना चाहेंगे?"
                            : (state.getRepeatCount() == 2
                                    ? "माफ़ कीजिए, तारीख़ स्पष्ट नहीं हुई। कोई एक दिन बताएं जैसे सोमवार या मंगलवार।"
                                    : "आपने जो तारीख़ बताई वह स्पष्ट नहीं है। कृपया सिर्फ एक दिन बताएं।");
                    aiResponse = personalizeSlotPrompt(basePrompt, state);
                }
            }
            case "ASK_TIME" -> {
                if (availabilityConflict) {
                    Map<String, Object> doc = clinicService.getWorkingDoctor(state);
                    if (doc != null) {
                        String patientNamePart = formatPersonalizedName(state.getPatientName());
                        String doctorNamePart = speechFormatter.formatDoctorName(doc.get("name").toString());
                        String startTimePart = formatConflictTime((String) doc.get("start"));
                        String endTimePart = formatConflictTime((String) doc.get("end"));
                        aiResponse = patientNamePart + ", " + doctorNamePart + " " + startTimePart + " से " + endTimePart + " तक उपलब्ध हैं। कृपया इसी समय सीमा में कोई समय चुनें।";
                    } else {
                        aiResponse = formatPersonalizedName(state.getPatientName()) + ", डॉक्टर इस समय उपलब्ध नहीं हैं। कृपया दूसरा समय चुनें।";
                    }
                } else {
                    String basePrompt = state.getRepeatCount() <= 1
                            ? "कृपया समय बता दीजिए।"
                            : (state.getRepeatCount() == 2
                                    ? "माफ़ कीजिए, समय स्पष्ट नहीं हुआ। कृपया बताएं जैसे सुबह दस बजे या दोपहर बारह बजे।"
                                    : "कृपया सटीक समय बताएं जैसे दोपहर बारह बजे या शाम चार बजे।");
                    aiResponse = personalizeSlotPrompt(basePrompt, state);
                }
            }
            case "NEG_CONFIRM" -> aiResponse = "ठीक है " + formatPersonalizedName(state.getPatientName()) + ", कृपया नया समय बताइए।";
            case "POST_CONFIRM" -> aiResponse = "धन्यवाद " + formatPersonalizedName(state.getPatientName()) + "। आपकी अपॉइंटमेंट कन्फर्म हो गई है। क्या आपको और मदद चाहिए?";
            case "CANCEL" -> aiResponse = "ठीक है, अपॉइंटमेंट कैंसिल कर दी गई है।";
            case "END" -> aiResponse = "धन्यवाद। आपका दिन शुभ हो।";
            case "MULTI_DATE" -> aiResponse = "आपने दो तारीखें बताई हैं। कृपया एक तारीख़ चुनें।";
            case "INFORM" -> aiResponse = systemData;
            case "CONFIRM_DETAILS" -> {
                String dayH = speechFormatter.toHindiDay(speechFormatter.getDayOfWeek(state.getDate()));
                String dateH = speechFormatter.toHindiDate(state.getDate());
                String timeN = speechFormatter.toNaturalTime(state.getTime());
                String docN = speechFormatter.formatDoctorName(state.getAssignedDoctor());
                String dept = speechFormatter.formatDeptName(state.getDepartment());
                aiResponse = formatPersonalizedName(state.getPatientName()) + ", आपका अपॉइंटमेंट " + dayH + ", " + dateH + " को "
                         + timeN + " " + docN + " (" + dept + " विभाग) के साथ है, क्या मैं इसे कन्फर्म कर दूँ?";
            }
            default -> {
                try {
                    aiResponse = groqClient.generateGroqResponse(contextStr);
                } catch (Exception e) {
                    aiResponse = "माफ़ कीजिए, कुछ तकनीकी दिक्कत आई।";
                }
            }
        }

        state.appendMessage("assistant", aiResponse);
        log.debug("AI Response: {}", aiResponse);

        log.info("[STATE TRANSITION] Mode={}, ExpectedField={}, PatientName={}, SuggestedDept={}, ConfirmedDept={}, Date={}, Time={}, NextAction={}",
                 state.getMode(),
                 state.getLastAskedField() != null ? state.getLastAskedField() : "None",
                 state.getPatientName() != null ? state.getPatientName() : "None",
                 state.getSuggestedDepartment() != null ? state.getSuggestedDepartment() : "None",
                 state.getDepartment() != null ? state.getDepartment() : "None",
                 state.getDate() != null ? state.getDate() : "None",
                 state.getTime() != null ? state.getTime().toString() : "None",
                 nextAction);

        log.debug("Synthesizing speech...");
        String audioBase64;
        try {
            long startTts = System.currentTimeMillis();
            audioBase64 = sarvamClient.synthesizeSpeech(aiResponse);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
        } catch (Exception e) {
            log.error("Failed TTS: {}", e.getMessage());
            audioBase64 = "";
        }

        metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
        log.info("[METRICS] Processed active request. Latency={} ms", System.currentTimeMillis() - startReqTime);
        return new VoiceResponse(transcription, aiResponse, audioBase64, endCall, state.getSessionId());
    }

    private void forceAcceptForField(SessionState state, String cleanText, String rawTranscription,
            LlmExtractionResponse extracted) {
        String field = state.getLastAskedField();
        if (field == null)
            return;
        switch (field) {
            case "name" -> {
                if (state.getPatientName() == null && isValidName(cleanText)) {
                    state.setPatientName(cleanText);
                    log.debug("Force-accepted name: {}", cleanText);
                }
            }
            case "department" -> {
                if (state.getDepartment() == null) {
                    String dept = normalizeDepartment(
                            isValidValue(extracted.getDepartment()) ? extracted.getDepartment() : cleanText);
                    if (isValidValue(dept)) {
                        state.setDepartment(dept);
                        log.debug("Force-accepted department: {}", dept);
                    }
                }
            }
            case "date" -> {
                if (state.getDate() == null && isValidValue(extracted.getDate()) && !isPastDate(extracted.getDate())) {
                    state.setDate(extracted.getDate());
                    log.debug("Force-accepted date: {}", extracted.getDate());
                }
            }
            case "time" -> {
                if (state.getTime() == null) {
                    String t = isValidValue(extracted.getTime()) ? extracted.getTime() : rawTranscription.trim();
                    java.time.LocalTime parsed = speechFormatter.parseTimeToLocalTime(t);
                    if (parsed == null) {
                        parsed = java.time.LocalTime.of(12, 0);
                    }
                    state.setTime(parsed);
                    log.debug("Force-accepted time: {}", parsed);
                }
            }
        }
    }

    private boolean isValidValue(String val) {
        return val != null && !val.trim().isEmpty() && !val.trim().equalsIgnoreCase("null");
    }

    private static final java.util.Set<String> INVALID_NAME_TOKENS = java.util.Set.of(
            "appointment", "अपॉइंटमेंट", "confirmation", "कन्फर्म", "booking",
            "reschedule", "रीशेड्यूल", "neurology", "न्यूरोलॉजी", "orthopedic",
            "cardiology", "dermatology", "pediatrics", "physician", "doctor");

    private boolean isValidName(String name) {
        if (name == null)
            return false;
        String n = name.trim();
        if (n.split("\\s+").length > 3)
            return false;
        String lower = n.toLowerCase();
        for (String bad : INVALID_NAME_TOKENS) {
            if (lower.contains(bad))
                return false;
        }
        return !lower.contains("appointment") && !lower.contains("अपॉइंटमेंट")
                && !lower.contains("चाहिए") && !lower.contains("बुक") && !lower.contains("लेना")
                && !lower.contains("करना") && !lower.contains("दिखाना") && !lower.contains("doctor")
                && !lower.contains("विभाग") && !lower.contains("क्लिनिक");
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty())
            return false;
        String t = time.trim().toUpperCase();
        if (t.matches("\\d{1,2}\\s*(AM|PM)"))
            return true;
        if (t.matches("\\d{1,2}:\\d{2}(\\s*(AM|PM))?"))
            return true;
        if (time.contains("बजे") || time.contains("सुबह") || time.contains("दोपहर") || time.contains("शाम"))
            return true;
        if (t.matches("\\d{1,2}")) {
            int h = Integer.parseInt(t);
            return h >= 1 && h <= 12;
        }
        String low = time.toLowerCase();
        for (String day : new String[] { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
                "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार",
                "april", "may", "june", "january", "february", "march", "july", "august",
                "अप्रैल", "मार्च", "जनवरी" }) {
            if (low.contains(day))
                return false;
        }
        return false;
    }

    private String getExplicitDepartment(String transcription) {
        if (transcription == null) return null;
        
        String normalized = departmentNormalizationService.normalize(transcription);
        if (normalized != null) {
            return normalized;
        }

        String s = transcription.toLowerCase().trim();
        if (s.contains("cardiology") || s.contains("कार्डियोलॉजी")) {
            return "Cardiology";
        }
        if (s.contains("orthopedic") || s.contains("ऑर्थोपेडिक")) {
            return "Orthopedic";
        }
        if (s.contains("neurology") || s.contains("न्यूरोलॉजी")) {
            return "Neurology";
        }
        if (s.contains("dermatology") || s.contains("डर्मेटोलॉजी")) {
            return "Dermatology";
        }
        if (s.contains("general physician") || s.contains("जनरल फिजिशियन") || s.contains("samanya physician") || s.contains("सामान्य फिजिशियन")) {
            return "General Physician";
        }
        if (s.contains("pediatrics") || s.contains("पीडियाट्रिक्स") || s.contains("बाल रोग")) {
            return "Pediatrics";
        }
        return null;
    }

    private String normalizeDepartment(String input) {
        if (input == null)
            return null;

        String normalized = departmentNormalizationService.normalize(input);
        if (normalized != null) {
            return normalized;
        }

        String s = input.toLowerCase().trim();
        if (s.contains("ortho") || s.contains("हड्डी") || s.contains("bone") || s.contains("अस्थि")
                || s.contains("घुटने") || s.contains("जोड़") || s.contains("कमर") || s.contains("बाँह्")
                || s.contains("टाँग"))
            return "Orthopedic";
        if (s.contains("cardio") || s.contains("heart") || s.contains("दिल") || s.contains("छाती")
                || s.contains("सांस"))
            return "Cardiology";
        if (s.contains("neuro") || s.contains("brain") || s.contains("दिमाग") || s.contains("सिरदर्द")
                || s.contains("मिर्गी"))
            return "Neurology";
        if (s.contains("derma") || s.contains("skin") || s.contains("त्वचा") || s.contains("खाज") || s.contains("दाने")
                || s.contains("पिंपल"))
            return "Dermatology";
        if (s.contains("general") || s.contains("सामान्य") || s.contains("gp") || s.contains("बुखार")
                || s.contains("जुकाम"))
            return "General Physician";
        if (s.contains("paed") || s.contains("pedia") || s.contains("child") || s.contains("बच्च"))
            return "Pediatrics";
        
        for (String dept : SessionState.ALLOWED_DEPARTMENTS) {
            if (dept.equalsIgnoreCase(s)) {
                return dept;
            }
        }
        return null;
    }

    private boolean isPastDate(String dateStr) {
        if (dateStr == null)
            return false;
        try {
            java.time.LocalDate parsed = java.time.LocalDate.parse(dateStr);
            return parsed.isBefore(java.time.LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    private String getNextExpectedField(SessionState state) {
        if (state.getPatientName() == null || state.getPatientName().trim().isEmpty() || !isValidName(state.getPatientName())) {
            return "name";
        }
        if (state.getDepartment() == null || !SessionState.ALLOWED_DEPARTMENTS.contains(state.getDepartment())) {
            return "department";
        }
        if (state.getDate() == null || isPastDate(state.getDate()) || state.getDate().contains(",")) {
            return "date";
        }
        if (state.getTime() == null || !clinicService.isTimeInSlot(state.getTime(), state)) {
            return "time";
        }
        return "confirmation";
    }

    private boolean areAllSlotsValid(SessionState state) {
        if (state.getPatientName() == null || state.getPatientName().trim().isEmpty() || !isValidName(state.getPatientName())) {
            return false;
        }
        if (state.getDepartment() == null || !SessionState.ALLOWED_DEPARTMENTS.contains(state.getDepartment())) {
            return false;
        }
        if (state.getDate() == null || isPastDate(state.getDate()) || state.getDate().contains(",")) {
            return false;
        }
        if (state.getTime() == null || !clinicService.isTimeInSlot(state.getTime(), state)) {
            return false;
        }
        return true;
    }

    private String formatPersonalizedName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        String normalized = name.trim();
        if (normalized.equalsIgnoreCase("Rahul")) {
            normalized = "राहुल";
        }
        if (normalized.equalsIgnoreCase("Shardul")) {
            normalized = "शार्दुल";
        }
        if (normalized.endsWith("जी")) {
            return normalized;
        }
        return normalized + " जी";
    }

    private int getUserInteractionCount(SessionState state) {
        int count = 0;
        for (Map<String, String> msg : state.getMessageHistory()) {
            if ("user".equals(msg.get("role"))) {
                count++;
            }
        }
        return count;
    }

    private String getGreetingWord() {
        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        int hour = now.getHour();
        if (hour >= 5 && hour < 12) {
            return "सुप्रभात";
        } else if (hour >= 12 && hour < 17) {
            return "नमस्कार";
        } else if (hour >= 17 && hour < 22) {
            return "शुभ संध्या";
        } else {
            return "नमस्ते";
        }
    }

    private String personalizeSlotPrompt(String basePrompt, SessionState state) {
        if (state.getPatientName() == null || state.getPatientName().trim().isEmpty() || state.getRepeatCount() > 1) {
            return basePrompt;
        }
        int count = getUserInteractionCount(state);
        if (count % 2 == 0) {
            return formatPersonalizedName(state.getPatientName()) + ", " + basePrompt;
        }
        return basePrompt;
    }

    private String formatConflictTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return "";
        }
        java.time.LocalTime parsed = speechFormatter.parseTimeToLocalTime(timeStr);
        if (parsed == null) {
            return timeStr;
        }
        int hour24 = parsed.getHour();
        int minute = parsed.getMinute();
        int h12 = hour24 == 0 ? 12 : (hour24 > 12 ? hour24 - 12 : hour24);
        
        String prefix;
        if      (hour24 >= 5  && hour24 < 12) prefix = "सुबह";
        else if (hour24 == 12)                prefix = "दोपहर";
        else if (hour24 >= 12 && hour24 < 17) prefix = "दोपहर";
        else if (hour24 >= 17 && hour24 < 21) prefix = "शाम";
        else                                  prefix = "रात";

        if (minute == 0) {
            return prefix + " " + h12 + " बजे";
        } else {
            return prefix + " " + h12 + ":" + String.format("%02d", minute) + " बजे";
        }
    }

    private String buildBookingSummary(SessionState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("BOOKING SUMMARY:\n");
        sb.append("Name: ").append(state.getPatientName() != null ? formatPersonalizedName(state.getPatientName()) : "Not provided")
                .append("\n");
        sb.append("Department: ").append(state.getDepartment() != null ? state.getDepartment() : "Not provided")
                .append("\n");
        String day = speechFormatter.getDayOfWeek(state.getDate());
        sb.append("Date: ").append(day != null ? day : (state.getDate() != null ? state.getDate() : "Not provided"))
                .append("\n");
        sb.append("Time: ").append(state.getTime() != null ? state.getTime() : "Not provided").append("\n");
        sb.append("Doctor: ").append(state.getAssignedDoctor() != null ? state.getAssignedDoctor() : "To be assigned")
                .append("\n");
        return sb.toString();
    }

    private String buildLLMContext(SessionState state, String nextAction, String systemData) {
        StringBuilder sb = new StringBuilder();
        sb.append("USER STATE:\n");
        sb.append("Name: ").append(state.getPatientName() != null ? state.getPatientName() : "Not provided")
                .append("\n");
        sb.append("Department: ").append(state.getDepartment() != null ? state.getDepartment() : "Not provided")
                .append("\n");
        sb.append("Date: ").append(state.getDate() != null ? state.getDate() : "Not provided");
        String day = speechFormatter.getDayOfWeek(state.getDate());
        if (day != null)
            sb.append(" (").append(day).append(")");
        sb.append("\n");
        sb.append("Time: ").append(state.getTime() != null ? state.getTime() : "Not provided").append("\n\n");

        sb.append("SYSTEM DATA:\n");
        sb.append(systemData != null && !systemData.isEmpty() ? systemData : "None").append("\n\n");

        sb.append("NEXT ACTION:\n");
        sb.append(nextAction).append("\n");

        return sb.toString();
    }
}
