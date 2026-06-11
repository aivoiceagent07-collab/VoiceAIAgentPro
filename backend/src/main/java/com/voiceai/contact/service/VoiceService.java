package com.voiceai.contact.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.voiceai.contact.dto.VoiceResponse;
import com.voiceai.contact.dto.LlmExtractionResponse;
import com.voiceai.contact.model.SessionState;

import java.util.*;

@Service
public class VoiceService {

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

    public VoiceService(DateNormalizerService dateNormalizerService,
                        SessionManagerService sessionManagerService,
                        InputValidatorService inputValidatorService,
                        SarvamClient sarvamClient,
                        GroqClient groqClient,
                        ClinicService clinicService,
                        SpeechFormatter speechFormatter) {
        this.dateNormalizerService = dateNormalizerService;
        this.sessionManagerService = sessionManagerService;
        this.inputValidatorService = inputValidatorService;
        this.sarvamClient = sarvamClient;
        this.groqClient = groqClient;
        this.clinicService = clinicService;
        this.speechFormatter = speechFormatter;
    }

    public VoiceResponse processVoice(MultipartFile audio, String sessionId) throws Exception {
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
                System.out.println("[LOG] Initial greeting sent.");
                return new VoiceResponse("", greetMsg, sarvamClient.synthesizeSpeech(greetMsg), false, state.getSessionId());
            } else {
                return new VoiceResponse("", "", "", false, state.getSessionId());
            }
        }

        // 2. STT (Speech to Text) via Sarvam
        System.out.println("Transcribing audio...");
        String transcription;
        try {
            transcription = sarvamClient.transcribeAudio(audio);
            System.out.println("Transcription: " + transcription);
        } catch (Exception e) {
            System.err.println("Failed STT: " + e.getMessage());
            transcription = "नमस्ते, यह एक टेस्ट है।"; // Fallback text
        }

        if (transcription == null || transcription.trim().isEmpty()) {
            System.out.println("No speech detected. Returning fallback audio directly and skipping LLM.");
            String fallbackMsg = "मुझे आपकी आवाज़ सुनाई नहीं दी। कृपया फिर से प्रयास करें।";
            String audioBase64 = sarvamClient.synthesizeSpeech(fallbackMsg);
            return new VoiceResponse("", fallbackMsg, audioBase64, false, state.getSessionId());
        }

        // Script normalization (Bengali → Devanagari/Canonical terms)
        transcription = transcription
                .replace("অর্থোপেডিক", "Orthopedic")
                .replace("অস্থি",      "Orthopedic")
                .replace("ডাক্তার",    "Doctor")
                .replace("অ্যাপয়েন্টমেন্ট", "Appointment")
                .replace("রাহুল",      "राहुल")
                .replace("কাল",       "कल")
                .replace("आज",        "आज");
        transcription = normalizeDepartment(transcription);
        System.out.println("[LOG] Normalized input: " + transcription);

        // Input validation
        if (!inputValidatorService.isValidInput(transcription, state.getLastAskedField())) {
            System.out.println("[LOG] Input rejected by validator: " + transcription);
            String errorMsg = "माफ़ कीजिए, मुझे समझ नहीं आया। क्या आप फिर से बता सकते हैं?";
            state.appendMessage("assistant", errorMsg);
            return new VoiceResponse(transcription, errorMsg, sarvamClient.synthesizeSpeech(errorMsg), false, state.getSessionId());
        }

        // Add user text to memory
        state.appendMessage("user", transcription);

        // Intent Classification
        String intent = groqClient.classifyIntent(transcription, state);
        
        // Hard Override for Intents (deterministic keyword rules override LLM)
        String cTextCheck = transcription.toLowerCase().trim();

        // Confirmation override — MUST happen before SOFT_END/END check
        boolean hasNegation = cTextCheck.contains("नहीं") || cTextCheck.contains("मत") || cTextCheck.contains("ना ");
        boolean isConfirmKeyword = !hasNegation && (
                cTextCheck.contains("हाँ") || cTextCheck.contains("हां") || cTextCheck.contains("yes") ||
                cTextCheck.contains("कर दीजिए") || cTextCheck.contains("कर दो") || cTextCheck.contains("बिल्कुल") ||
                cTextCheck.contains("कन्फर्म") || cTextCheck.contains("ठीक है") || cTextCheck.contains("जी") || cTextCheck.contains("sure"));
        
        boolean isNegativeConfirm = hasNegation && state.getMode() == SessionState.Mode.CONFIRMATION
                && (cTextCheck.contains("नहीं") || cTextCheck.contains("ठीक नहीं") || cTextCheck.contains("कन्फर्म नहीं"));
        
        if (isConfirmKeyword && state.getMode() == SessionState.Mode.CONFIRMATION) {
            intent = "CONTINUE";
        } else if (cTextCheck.equals("नहीं") || cTextCheck.equals("बस") || cTextCheck.equals("no") ||
                   cTextCheck.contains("धन्यवाद") || cTextCheck.contains("ज़रूरत नहीं") ||
                   cTextCheck.contains("thank you") || cTextCheck.contains("thanks") || cTextCheck.contains("डिस्कनेक्ट")) {
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
            if (!intent.equals("CANCEL") && state.getMode() == SessionState.Mode.BOOKING && state.getPatientName() == null) {
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

        System.out.println("Classified Intent: " + intent);

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
            System.out.println("[LOG] RESCHEDULE triggered from POST_CONFIRM");
            return new VoiceResponse(transcription, reschMsg, sarvamClient.synthesizeSpeech(reschMsg), false, state.getSessionId());
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
            return new VoiceResponse(transcription, aiResponse, sarvamClient.synthesizeSpeech(aiResponse), true, state.getSessionId());
        }

        // POST_CONFIRM
        if (state.isConfirmed() && state.getMode() == SessionState.Mode.POST_CONFIRM) {
            String answer = speechFormatter.resolvePostConfirmQuery(cTextCheck, state);
            if (answer != null) {
                state.appendMessage("assistant", answer);
                return new VoiceResponse(transcription, answer, sarvamClient.synthesizeSpeech(answer), false, state.getSessionId());
            }
        }

        // Confirmed general booking query
        if (intent.equals("ASK_QUERY") && state.isConfirmed()) {
            String dayHindi    = speechFormatter.toHindiDay(speechFormatter.getDayOfWeek(state.getDate()));
            String timeNatural = speechFormatter.toNaturalTime(state.getTime());
            String docName     = speechFormatter.formatDoctorName(state.getAssignedDoctor());
            String aiResponse  = "आपका अपॉइंटमेंट " + dayHindi + " को " + timeNatural + " " + docName + " के साथ है।";
            state.setLastAskedField(null);
            state.appendMessage("assistant", aiResponse);
            return new VoiceResponse(transcription, aiResponse, sarvamClient.synthesizeSpeech(aiResponse), false, state.getSessionId());
        }

        // Entity Extraction
        String dateContext = dateNormalizerService.getDateContext(transcription);
        System.out.println("Extracting AI entities for: " + transcription);
        LlmExtractionResponse extracted = groqClient.extractGroqEntities(transcription, dateContext, state);

        int wordCount = transcription.split("\\s+").length;
        if (wordCount <= 3 && state.getLastAskedField() != null) {
            extracted.setIsOutOfScope(false);
        }

        // Hard Out-of-Scope gate
        if (extracted.getIsOutOfScope() != null && extracted.getIsOutOfScope()
                && (extracted.getIsQuerying() == null || !extracted.getIsQuerying())) {
            String oosMsg = "माफ़ कीजिए, मैं सिर्फ अपॉइंटमेंट बुकिंग में आपकी मदद कर सकती हूँ।";
            state.appendMessage("assistant", oosMsg);
            return new VoiceResponse(transcription, oosMsg, sarvamClient.synthesizeSpeech(oosMsg), false, state.getSessionId());
        }

        System.out.println("[LOG] Extracted: name=" + extracted.getName()
            + " dept=" + extracted.getDepartment()
            + " date=" + extracted.getDate()
            + " time=" + extracted.getTime());

        boolean timeInvalid = false;
        String systemData = "";
        String nextAction = "";
        boolean endCall = false;

        // Safe Entity Hydration
        if (!state.isConfirmed() && state.getMode() != SessionState.Mode.CONFIRMATION) {
            String cleanText = transcription.replaceAll("[।.,!?\\s]+", "").trim();

            if (state.getRepeatCount() >= 2 && state.getLastAskedField() != null) {
                System.out.println("[LOG] Loop detected (repeat=" + state.getRepeatCount() + "), force-accepting for: " + state.getLastAskedField());
                forceAcceptForField(state, cleanText, transcription, extracted);
                state.resetRepeatCount();
            } else if (wordCount <= 3 && state.getLastAskedField() != null) {
                if (state.getLastAskedField().equals("name") && state.getPatientName() == null) {
                    String val = extracted.getName();
                    if (isValidValue(val) && isValidName(val)) {
                        state.setPatientName(val);
                    } else if (isValidName(cleanText)) {
                        state.setPatientName(cleanText);
                        System.out.println("[LOG] Name via heuristic fallback: " + cleanText);
                    }
                } else if (state.getLastAskedField().equals("department") && state.getDepartment() == null) {
                    String val = normalizeDepartment(extracted.getDepartment());
                    if (isValidValue(val)) {
                        state.setDepartment(val);
                    } else {
                        String fallback = normalizeDepartment(cleanText);
                        if (isValidValue(fallback)) state.setDepartment(fallback);
                    }
                } else if (state.getLastAskedField().equals("date")
                        && (state.getDate() == null || state.getMode() == SessionState.Mode.RESCHEDULE)) {
                    String weekdayDate = speechFormatter.resolveWeekdayFromText(transcription);
                    String val = (weekdayDate != null) ? weekdayDate : extracted.getDate();
                    System.out.println("[LOG] Date candidate: weekday=" + weekdayDate + " llm=" + extracted.getDate() + " using=" + val);
                    if (isValidValue(val) && val.contains(",")) {
                        systemData = "MULTI_DATE";
                    } else if (isValidValue(val)) {
                        if (isPastDate(val)) systemData = "PAST_DATE";
                        else state.setDate(val);
                    }
                }
            } else if (state.getLastAskedField() != null && state.getLastAskedField().equals("time") && state.getTime() == null) {
                String candidateTime = isValidValue(extracted.getTime()) ? extracted.getTime() : null;
                if (candidateTime == null && !transcription.replaceAll("[^0-9]", "").isEmpty()) {
                    candidateTime = transcription.trim();
                }
                if (candidateTime != null && isValidTimeFormat(candidateTime)) {
                    if (clinicService.isTimeInSlot(candidateTime, state)) state.setTime(candidateTime);
                    else timeInvalid = true;
                } else if (candidateTime != null) {
                    timeInvalid = true;
                }
            } else if (!"PAST_DATE".equals(systemData)) {
                String eName = extracted.getName();
                if (isValidValue(eName) && isValidName(eName) && state.getPatientName() == null)
                    state.setPatientName(eName);

                String eDept = normalizeDepartment(extracted.getDepartment());
                if (isValidValue(eDept) && state.getDepartment() == null)
                    state.setDepartment(eDept);

                String _weekdayDate = speechFormatter.resolveWeekdayFromText(transcription);
                String eDate = (_weekdayDate != null) ? _weekdayDate : extracted.getDate();
                System.out.println("[LOG] Multi-word date candidate: weekday=" + _weekdayDate + " llm=" + extracted.getDate() + " using=" + eDate);
                if (isValidValue(eDate) && eDate.contains(",")) {
                    systemData = "MULTI_DATE";
                } else if (isValidValue(eDate) && (state.getDate() == null || state.getMode() == SessionState.Mode.RESCHEDULE)) {
                    if (isPastDate(eDate)) systemData = "PAST_DATE";
                    else state.setDate(eDate);
                }

                String eTime = extracted.getTime();
                if (isValidValue(eTime) && state.getTime() == null && isValidTimeFormat(eTime)) {
                    if (clinicService.isTimeInSlot(eTime, state)) state.setTime(eTime);
                    else timeInvalid = true;
                }
            }
        }

        boolean justEnteredConfirmation = false;
        if (state.getPatientName() != null && state.getDepartment() != null && state.getDate() != null && state.getTime() != null && !state.isConfirmed()) {
            if (state.getMode() == SessionState.Mode.BOOKING || state.getMode() == SessionState.Mode.RESCHEDULE) {
                state.setMode(SessionState.Mode.CONFIRMATION);
                justEnteredConfirmation = true;
                if (state.getAssignedDoctor() == null) {
                    state.setAssignedDoctor(clinicService.matchDoctorRaw(state));
                }
            }
        }

        boolean isCancel = intent.equals("CANCEL") || cTextCheck.contains("कैंसिल") || cTextCheck.contains("cancel");
        boolean isEnd = intent.equals("END") || cTextCheck.equals("नहीं") || cTextCheck.equals("बस") || cTextCheck.contains("धन्यवाद") || cTextCheck.contains("डिस्कनेक्ट");
        boolean isReschedule = intent.equals("RESCHEDULE")
            || cTextCheck.contains("रीशेड्यूल") || cTextCheck.contains("रिशेड्यूल")
            || cTextCheck.contains("रीशिड्यूल") || cTextCheck.contains("reschedule")
            || cTextCheck.contains("दूसरे दिन") || cTextCheck.contains("दिन बदलना") || cTextCheck.contains("बदलना है");
        boolean isPositive = !justEnteredConfirmation && (isConfirmKeyword || (extracted.getIsConfirming() != null && extracted.getIsConfirming()) || intent.equals("CONTINUE"));

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
        } else if (isReschedule && (state.isConfirmed() || state.getMode() == SessionState.Mode.POST_CONFIRM || state.getMode() == SessionState.Mode.CONFIRMATION)) {
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
                        System.out.println("[LOG] RESCHEDULE: date updated from query: " + state.getDate() + " → " + _wd);
                        state.setDate(_wd);
                    } else if (isValidValue(extracted.getDate()) && !extracted.getDate().contains(",")) {
                        String llmD = extracted.getDate();
                        if (!llmD.equals(state.getDate()) && !isPastDate(llmD)) {
                            System.out.println("[LOG] RESCHEDULE: date updated from LLM query: " + state.getDate() + " → " + llmD);
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
        System.out.println("Generated LLM Context:\n" + contextStr);

        String aiResponse;
        switch (nextAction) {
            case "ASK_NAME"         -> aiResponse = "कृपया अपना नाम बताइए।";
            case "ASK_DEPARTMENT"   -> aiResponse = state.getRepeatCount() <= 1
                ? "किस विभाग में दिखाना है?"
                : "कृपया बताएं आपको किस तरह के डॉक्टर को दिखाना है, जैसे हड्डी, दिल, नसें या त्वचा।";
            case "ASK_DATE"         -> aiResponse = state.getRepeatCount() <= 1
                ? "किस दिन आना चाहेंगे?"
                : (state.getRepeatCount() == 2
                    ? "माफ़ कीजिए, तारीख़ स्पष्ट नहीं हुई। कोई एक दिन बताएं जैसे सोमवार या मंगलवार।"
                    : "आपने जो तारीख़ बताई वह स्पष्ट नहीं है। कृपया सिर्फ एक दिन बताएं।");
            case "ASK_TIME"         -> aiResponse = state.getRepeatCount() <= 1
                ? "कृपया समय बता दीजिए।"
                : (state.getRepeatCount() == 2
                    ? "माफ़ कीजिए, समय स्पष्ट नहीं हुआ। कृपया बताएं जैसे सुबह दस बजे या दोपहर बारह बजे।"
                    : "कृपया सटीक समय बताएं जैसे दोपहर बारह बजे या शाम चार बजे।");
            case "NEG_CONFIRM"      -> aiResponse = "ठीक है, कृपया नया समय बताइए।";
            case "POST_CONFIRM"     -> aiResponse = "आपकी अपॉइंटमेंट कन्फर्म हो गई है। क्या आपको और मदद चाहिए?";
            case "CANCEL"           -> aiResponse = "ठीक है, अपॉइंटमेंट कैंसिल कर दी गई है।";
            case "END"              -> aiResponse = "धन्यवाद। आपका दिन शुभ हो।";
            case "MULTI_DATE"       -> aiResponse = "आपने दो तारीखें बताई हैं। कृपया एक तारीख़ चुनें।";
            case "INFORM"           -> aiResponse = systemData;
            case "CONFIRM_DETAILS"  -> {
                String dayH  = speechFormatter.toHindiDay(speechFormatter.getDayOfWeek(state.getDate()));
                String dateH = speechFormatter.toHindiDate(state.getDate());
                String timeN = speechFormatter.toNaturalTime(state.getTime());
                String docN  = speechFormatter.formatDoctorName(state.getAssignedDoctor());
                String dept  = speechFormatter.formatDeptName(state.getDepartment());
                aiResponse = "आपका अपॉइंटमेंट " + dayH + ", " + dateH + " को "
                    + timeN + " " + docN + " (" + dept + " विभाग) के साथ है, क्या मैं इसे कन्फर्म कर दूँ?";
            }
            default -> {
                try { aiResponse = groqClient.generateGroqResponse(contextStr); }
                catch (Exception e) { aiResponse = "माफ़ कीजिए, कुछ तकनीकी दिक्कत आई।"; }
            }
        }

        state.appendMessage("assistant", aiResponse);
        System.out.println("[LOG] AI Response: " + aiResponse);

        System.out.println("Synthesizing speech...");
        String audioBase64;
        try {
            audioBase64 = sarvamClient.synthesizeSpeech(aiResponse);
        } catch (Exception e) {
            System.err.println("Failed TTS: " + e.getMessage());
            audioBase64 = "";
        }

        return new VoiceResponse(transcription, aiResponse, audioBase64, endCall, state.getSessionId());
    }

    private void forceAcceptForField(SessionState state, String cleanText, String rawTranscription, LlmExtractionResponse extracted) {
        String field = state.getLastAskedField();
        if (field == null) return;
        switch (field) {
            case "name" -> {
                if (state.getPatientName() == null && isValidName(cleanText)) {
                    state.setPatientName(cleanText);
                    System.out.println("[LOG] Force-accepted name: " + cleanText);
                }
            }
            case "department" -> {
                if (state.getDepartment() == null) {
                    String dept = normalizeDepartment(isValidValue(extracted.getDepartment()) ? extracted.getDepartment() : cleanText);
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
                    if (t.matches(".*\\d.*")) {
                        state.setTime(t);
                        System.out.println("[LOG] Force-accepted time: " + t);
                    }
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
        "cardiology", "dermatology", "pediatrics", "physician", "doctor"
    );

    private boolean isValidName(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.split("\\s+").length > 3) return false;
        String lower = n.toLowerCase();
        for (String bad : INVALID_NAME_TOKENS) { if (lower.contains(bad)) return false; }
        return !lower.contains("appointment") && !lower.contains("अपॉइंटमेंट")
            && !lower.contains("चाहिए") && !lower.contains("बुक") && !lower.contains("लेना")
            && !lower.contains("करना") && !lower.contains("दिखाना") && !lower.contains("doctor")
            && !lower.contains("विभाग") && !lower.contains("क्लिनिक");
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        String t = time.trim().toUpperCase();
        if (t.matches("\\d{1,2}\\s*(AM|PM)")) return true;
        if (t.matches("\\d{1,2}:\\d{2}(\\s*(AM|PM))?")) return true;
        if (time.contains("बजे") || time.contains("सुबह") || time.contains("दोपहर") || time.contains("शाम")) return true;
        if (t.matches("\\d{1,2}")) {
            int h = Integer.parseInt(t);
            return h >= 1 && h <= 12;
        }
        String low = time.toLowerCase();
        for (String day : new String[]{"monday","tuesday","wednesday","thursday","friday","saturday","sunday",
                "सोमवार","मंगलवार","बुधवार","गुरुवार","शुक्रवार","शनिवार","रविवार",
                "april","may","june","january","february","march","july","august",
                "अप्रैल","मार्च","जनवरी"}) {
            if (low.contains(day)) return false;
        }
        return false;
    }

    private String normalizeDepartment(String input) {
        if (input == null) return null;
        String s = input.toLowerCase().trim();
        if (s.contains("ortho") || s.contains("हड्डी") || s.contains("bone") || s.contains("अस्थि")
                || s.contains("घुटने") || s.contains("जोड़") || s.contains("कमर") || s.contains("बाँह्") || s.contains("टाँग")) return "Orthopedic";
        if (s.contains("cardio") || s.contains("heart") || s.contains("दिल") || s.contains("छाती") || s.contains("सांस")) return "Cardiology";
        if (s.contains("neuro") || s.contains("brain") || s.contains("दिमाग") || s.contains("सिरदर्द") || s.contains("मिर्गी")) return "Neurology";
        if (s.contains("derma") || s.contains("skin") || s.contains("त्वचा") || s.contains("खाज") || s.contains("दाने") || s.contains("पिंपल")) return "Dermatology";
        if (s.contains("general") || s.contains("सामान्य") || s.contains("gp") || s.contains("बुखार") || s.contains("जुकाम")) return "General Physician";
        if (s.contains("paed") || s.contains("pedia") || s.contains("child") || s.contains("बच्च")) return "Pediatrics";
        return input;
    }

    private boolean isPastDate(String dateStr) {
        if (dateStr == null) return false;
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
        sb.append("Name: ").append(state.getPatientName() != null ? state.getPatientName() : "Not provided").append("\n");
        sb.append("Department: ").append(state.getDepartment() != null ? state.getDepartment() : "Not provided").append("\n");
        String day = speechFormatter.getDayOfWeek(state.getDate());
        sb.append("Date: ").append(day != null ? day : (state.getDate() != null ? state.getDate() : "Not provided")).append("\n");
        sb.append("Time: ").append(state.getTime() != null ? state.getTime() : "Not provided").append("\n");
        sb.append("Doctor: ").append(state.getAssignedDoctor() != null ? state.getAssignedDoctor() : "To be assigned").append("\n");
        return sb.toString();
    }

    private String buildLLMContext(SessionState state, String nextAction, String systemData) {
        StringBuilder sb = new StringBuilder();
        sb.append("USER STATE:\n");
        sb.append("Name: ").append(state.getPatientName() != null ? state.getPatientName() : "Not provided").append("\n");
        sb.append("Department: ").append(state.getDepartment() != null ? state.getDepartment() : "Not provided").append("\n");
        sb.append("Date: ").append(state.getDate() != null ? state.getDate() : "Not provided");
        String day = speechFormatter.getDayOfWeek(state.getDate());
        if (day != null) sb.append(" (").append(day).append(")");
        sb.append("\n");
        sb.append("Time: ").append(state.getTime() != null ? state.getTime() : "Not provided").append("\n\n");
        
        sb.append("SYSTEM DATA:\n");
        sb.append(systemData != null && !systemData.isEmpty() ? systemData : "None").append("\n\n");

        sb.append("NEXT ACTION:\n");
        sb.append(nextAction).append("\n");

        return sb.toString();
    }
}
