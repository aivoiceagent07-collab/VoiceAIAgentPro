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

    public VoiceService(DateNormalizerService dateNormalizerService,
            SessionManagerService sessionManagerService,
            InputValidatorService inputValidatorService,
            SarvamClient sarvamClient,
            GroqClient groqClient,
            ClinicService clinicService,
            SpeechFormatter speechFormatter,
            DeterministicExtractionService deterministicExtractionService,
            DepartmentRoutingService departmentRoutingService,
            PerformanceMetricsService metricsService) {
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
                String greetMsg = "नमस्ते, मैं क्लिनिक की रिसेप्शनिस्ट हूँ। मैं आपकी क्या मदद कर सकती हूँ?";
                state.appendMessage("assistant", greetMsg);
                log.info("Initial greeting sent.");
                long startTts = System.currentTimeMillis();
                String audioBase64 = sarvamClient.synthesizeSpeech(greetMsg);
                metricsService.recordTts(System.currentTimeMillis() - startTts);
                metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
                return new VoiceResponse("", greetMsg, audioBase64, false,
                        state.getSessionId());
            } else {
                metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
                return new VoiceResponse("", "", "", false, state.getSessionId());
            }
        }

        // 2. STT (Speech to Text) via Sarvam
        log.info("Transcribing audio...");
        String transcription;
        long startStt = System.currentTimeMillis();
        try {
            transcription = sarvamClient.transcribeAudio(audio);
            metricsService.recordStt(System.currentTimeMillis() - startStt);
            log.info("Transcription: {}", transcription);
        } catch (Exception e) {
            log.error("Failed STT: {}", e.getMessage());
            metricsService.recordStt(System.currentTimeMillis() - startStt);
            transcription = "नमस्ते, यह एक टेस्ट है।"; // Fallback text
        }

        if (transcription == null || transcription.trim().isEmpty()) {
            log.info("No speech detected. Returning fallback audio directly and skipping LLM.");
            String fallbackMsg = "मुझे आपकी आवाज़ सुनाई नहीं दी। कृपया फिर से प्रयास करें।";
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(fallbackMsg);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
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
        transcription = normalizeDepartment(transcription);
        log.info("Normalized input: {}", transcription);

        // Input validation
        if (!inputValidatorService.isValidInput(transcription, state.getLastAskedField())) {
            log.info("Input rejected by validator: {}", transcription);
            String errorMsg = "माफ़ कीजिए, मुझे समझ नहीं आया। क्या आप फिर से बता सकते हैं?";
            state.appendMessage("assistant", errorMsg);
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(errorMsg);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            return new VoiceResponse(transcription, errorMsg, audioBase64, false,
                    state.getSessionId());
        }

        // Add user text to memory
        state.appendMessage("user", transcription);

        // 3. Deterministic Extraction Layer (Pre-LLM) & Symptom Department Routing
        DeterministicExtractionService.ExtractionResult detResult = deterministicExtractionService.extract(transcription, state);
        String predictedDept = null;
        if (state.getDepartment() == null) {
            predictedDept = departmentRoutingService.route(transcription);
            if (predictedDept != null) {
                state.setDepartment(predictedDept);
                metricsService.recordRouting(transcription.toLowerCase().contains(predictedDept.toLowerCase()) ? "dictionary" : "similarity");
            }
        }
        if ("department".equals(state.getLastAskedField()) && predictedDept != null) {
            detResult.setSuccess(true);
            detResult.setDepartment(predictedDept);
            detResult.setIntent("PROVIDE_INFO");
        }

        LlmExtractionResponse extracted;
        String intent;
        if (detResult.isSuccess()) {
            log.info("Deterministic extraction bypass triggered. ExpectedField={}", state.getLastAskedField());
            extracted = new LlmExtractionResponse();
            extracted.setIntent(detResult.getIntent());
            extracted.setName(detResult.getName());
            extracted.setDepartment(detResult.getDepartment() != null ? detResult.getDepartment() : state.getDepartment());
            extracted.setDate(detResult.getDate());
            extracted.setTime(detResult.getTime());
            extracted.setIsConfirming(detResult.getIsConfirming());
            extracted.setIsQuerying(detResult.getIsQuerying());
            extracted.setIsOutOfScope(detResult.getIsOutOfScope());
            intent = detResult.getIntent();
        } else {
            long startLlm = System.currentTimeMillis();
            String dateContext = dateNormalizerService.getDateContext(transcription);
            log.info("Extracting AI entities for: {}", transcription);
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

        log.info("Classified Intent: {}", intent);

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
            String reschMsg = "ठीक है, आप किस दिन अपॉइंटमेंट रखना चाहेंगे?";
            state.appendMessage("assistant", reschMsg);
            log.info("RESCHEDULE triggered from POST_CONFIRM");
            long startTts = System.currentTimeMillis();
            String audioBase64 = sarvamClient.synthesizeSpeech(reschMsg);
            metricsService.recordTts(System.currentTimeMillis() - startTts);
            metricsService.recordRequest(System.currentTimeMillis() - startReqTime);
            return new VoiceResponse(transcription, reschMsg, audioBase64, false,
                    state.getSessionId());
        }

        // Hard END
        boolean hardEnd = intent.equals("END")
                || cTextCheck.equals("नहीं") || cTextCheck.equals("नहीं।")
                || cTextCheck.equals("बस") || cTextCheck.equals("बस।")
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
            return new VoiceResponse(transcription, aiResponse, audioBase64, false,
                    state.getSessionId());
        }

        int wordCount = transcription.split("\\s+").length;
        if (wordCount <= 3 && state.getLastAskedField() != null) {
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
            return new VoiceResponse(transcription, oosMsg, audioBase64, false,
                    state.getSessionId());
        }

        log.info("Extracted: name={}, dept={}, date={}, time={}", extracted.getName(),
                extracted.getDepartment(), extracted.getDate(), extracted.getTime());

        boolean timeInvalid = false;
        String systemData = "";
        String nextAction = "";
        boolean endCall = false;

        // Safe Entity Hydration
        if (!state.isConfirmed() && state.getMode() != SessionState.Mode.CONFIRMATION) {
            String cleanText = transcription.replaceAll("[।.,!?\\s]+", "").trim();

            if (state.getRepeatCount() >= 2 && state.getLastAskedField() != null) {
                log.info("Loop detected (repeat={}), force-accepting for: {}", state.getRepeatCount(),
                        state.getLastAskedField());
                forceAcceptForField(state, cleanText, transcription, extracted);
                state.resetRepeatCount();
            } else {
                // Sequential hydration based on the expected field first
                String expectedField = state.getLastAskedField();
                if ("name".equals(expectedField) && state.getPatientName() == null) {
                    String val = extracted.getName();
                    if (isValidValue(val) && isValidName(val)) {
                        state.setPatientName(val);
                    } else if (isValidName(cleanText)) {
                        state.setPatientName(cleanText);
                        log.info("Name via heuristic fallback: {}", cleanText);
                    }
                } else if ("department".equals(expectedField) && state.getDepartment() == null) {
                    String val = normalizeDepartment(extracted.getDepartment());
                    if (isValidValue(val)) {
                        state.setDepartment(val);
                    } else {
                        String fallback = normalizeDepartment(cleanText);
                        if (isValidValue(fallback)) {
                            state.setDepartment(fallback);
                        }
                    }
                } else if ("date".equals(expectedField) && (state.getDate() == null || state.getMode() == SessionState.Mode.RESCHEDULE)) {
                    String weekdayDate = speechFormatter.resolveWeekdayFromText(transcription);
                    String val = (weekdayDate != null) ? weekdayDate : extracted.getDate();
                    log.info("Date candidate: weekday={}, llm={}, using={}", weekdayDate, extracted.getDate(), val);
                    if (isValidValue(val) && val.contains(",")) {
                        systemData = "MULTI_DATE";
                    } else if (isValidValue(val)) {
                        if (isPastDate(val)) {
                            systemData = "PAST_DATE";
                        } else {
                            state.setDate(val);
                        }
                    }
                } else if ("time".equals(expectedField) && state.getTime() == null) {
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
                            }
                        } else {
                            timeInvalid = true;
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
                    String eDept = normalizeDepartment(extracted.getDepartment());
                    if (isValidValue(eDept) && state.getDepartment() == null) {
                        state.setDepartment(eDept);
                    }
                    // Date fallback
                    String _weekdayDate = speechFormatter.resolveWeekdayFromText(transcription);
                    String eDate = (_weekdayDate != null) ? _weekdayDate : extracted.getDate();
                    if (isValidValue(eDate) && (state.getDate() == null || state.getMode() == SessionState.Mode.RESCHEDULE)) {
                        if (isPastDate(eDate)) {
                            systemData = "PAST_DATE";
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
                            }
                        }
                    }
                }
            }
        }

        boolean justEnteredConfirmation = false;
        if (state.getPatientName() != null && state.getDepartment() != null && state.getDate() != null
                && state.getTime() != null && !state.isConfirmed()) {
            if (state.getMode() == SessionState.Mode.BOOKING || state.getMode() == SessionState.Mode.RESCHEDULE) {
                state.setMode(SessionState.Mode.CONFIRMATION);
                justEnteredConfirmation = true;
                if (state.getAssignedDoctor() == null) {
                    state.setAssignedDoctor(clinicService.matchDoctorRaw(state));
                }
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
                        log.info("RESCHEDULE: date updated from query: {} → {}", state.getDate(), _wd);
                        state.setDate(_wd);
                    } else if (isValidValue(extracted.getDate()) && !extracted.getDate().contains(",")) {
                        String llmD = extracted.getDate();
                        if (!llmD.equals(state.getDate()) && !isPastDate(llmD)) {
                            log.info("RESCHEDULE: date updated from LLM query: {} → {}", state.getDate(), llmD);
                            state.setDate(llmD);
                        }
                    }
                }
                systemData = clinicService.buildAvailabilityResponse(state);
            }
        } else if (state.getMode() == SessionState.Mode.BOOKING || state.getMode() == SessionState.Mode.RESCHEDULE) {
            if (state.getPatientName() == null) {
                nextAction = "ASK_NAME";
                state.setLastAskedField("name");
                state.incrementRepeatCount();
            } else if (state.getDepartment() == null) {
                nextAction = "ASK_DEPARTMENT";
                state.setLastAskedField("department");
                state.incrementRepeatCount();
            } else if (state.getDate() == null) {
                nextAction = "ASK_DATE";
                state.setLastAskedField("date");
                state.incrementRepeatCount();
            } else if (state.getTime() == null) {
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
        log.info("Generated LLM Context:\n{}", contextStr);

        String aiResponse;
        switch (nextAction) {
            case "ASK_NAME" -> aiResponse = "कृपया अपना नाम बताइए।";
            case "ASK_DEPARTMENT" -> aiResponse = state.getRepeatCount() <= 1
                    ? "किस विभाग में दिखाना है?"
                    : "कृपया बताएं आपको किस तरह के डॉक्टर को दिखाना है, जैसे हड्डी, दिल, नसें या त्वचा।";
            case "ASK_DATE" -> aiResponse = state.getRepeatCount() <= 1
                    ? "किस दिन आना चाहेंगे?"
                    : (state.getRepeatCount() == 2
                            ? "माफ़ कीजिए, तारीख़ स्पष्ट नहीं हुई। कोई एक दिन बताएं जैसे सोमवार या मंगलवार।"
                            : "आपने जो तारीख़ बताई वह स्पष्ट नहीं है। कृपया सिर्फ एक दिन बताएं।");
            case "ASK_TIME" -> aiResponse = state.getRepeatCount() <= 1
                    ? "कृपया समय बता दीजिए।"
                    : (state.getRepeatCount() == 2
                            ? "माफ़ कीजिए, समय स्पष्ट नहीं हुआ। कृपया बताएं जैसे सुबह दस बजे या दोपहर बारह बजे।"
                            : "कृपया सटीक समय बताएं जैसे दोपहर बारह बजे या शाम चार बजे।");
            case "NEG_CONFIRM" -> aiResponse = "ठीक है, कृपया नया समय बताइए।";
            case "POST_CONFIRM" -> aiResponse = "आपकी अपॉइंटमेंट कन्फर्म हो गई है। क्या आपको और मदद चाहिए?";
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
                aiResponse = "आपका अपॉइंटमेंट " + dayH + ", " + dateH + " को "
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
        log.info("AI Response: {}", aiResponse);

        log.info("Synthesizing speech...");
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
                    System.out.println("[LOG] Force-accepted name: " + cleanText);
                }
            }
            case "department" -> {
                if (state.getDepartment() == null) {
                    String dept = normalizeDepartment(
                            isValidValue(extracted.getDepartment()) ? extracted.getDepartment() : cleanText);
                    if (isValidValue(dept)) {
                        state.setDepartment(dept);
                        System.out.println("[LOG] Force-accepted department: " + dept);
                    }
                }
            }
            case "date" -> {
                if (state.getDate() == null && isValidValue(extracted.getDate()) && !isPastDate(extracted.getDate())) {
                    state.setDate(extracted.getDate());
                    System.out.println("[LOG] Force-accepted date: " + extracted.getDate());
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
                    System.out.println("[LOG] Force-accepted time: " + parsed);
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

    private String normalizeDepartment(String input) {
        if (input == null)
            return null;
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
        return input;
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

    private String buildBookingSummary(SessionState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("BOOKING SUMMARY:\n");
        sb.append("Name: ").append(state.getPatientName() != null ? state.getPatientName() : "Not provided")
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
