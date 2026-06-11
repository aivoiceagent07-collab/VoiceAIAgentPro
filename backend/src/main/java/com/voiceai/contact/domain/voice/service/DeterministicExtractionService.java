package com.voiceai.contact.domain.voice.service;

import com.voiceai.contact.domain.voice.model.SessionState;
import com.voiceai.contact.domain.voice.util.SpeechFormatter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeterministicExtractionService {

    private final SpeechFormatter speechFormatter;
    private final InputValidatorService inputValidatorService;

    private static final Set<String> INVALID_NAME_TOKENS = Set.of(
            "appointment", "अपॉइंटमेंट", "confirmation", "कन्फर्म", "booking",
            "reschedule", "रीशेड्यूल", "neurology", "न्यूरोलॉजी", "orthopedic",
            "cardiology", "dermatology", "pediatrics", "physician", "doctor");

    public DeterministicExtractionService(SpeechFormatter speechFormatter, InputValidatorService inputValidatorService) {
        this.speechFormatter = speechFormatter;
        this.inputValidatorService = inputValidatorService;
    }

    public ExtractionResult extract(String text, SessionState state) {
        ExtractionResult result = new ExtractionResult();
        String clean = text.toLowerCase().trim();

        // 1. CONFIRMATION
        if ("confirmation".equals(state.getLastAskedField()) || state.getMode() == SessionState.Mode.CONFIRMATION) {
            boolean hasNegation = clean.contains("नहीं") || clean.contains("मत") || clean.contains("ना ") || clean.contains("no");
            boolean isConfirm = !hasNegation && (clean.contains("हाँ") || clean.contains("हां") || clean.contains("yes") ||
                    clean.contains("कर दीजिए") || clean.contains("कर दो") || clean.contains("बिल्कुल") ||
                    clean.contains("कन्फर्म") || clean.contains("ठीक है") || clean.contains("जी") || clean.contains("sure"));
            boolean isNeg = hasNegation && (clean.contains("नहीं") || clean.contains("ठीक नहीं") || clean.contains("कन्फर्म नहीं") || clean.contains("no"));

            if (isConfirm) {
                result.setSuccess(true);
                result.setIsConfirming(true);
                result.setIsQuerying(false);
                result.setIsOutOfScope(false);
                result.setIntent("CONTINUE");
                return result;
            } else if (isNeg) {
                result.setSuccess(true);
                result.setIsConfirming(false);
                result.setIsQuerying(false);
                result.setIsOutOfScope(false);
                result.setIntent("NEG_CONFIRM");
                return result;
            }
        }

        // 2. DATE
        if ("date".equals(state.getLastAskedField())) {
            // Check relative words
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            String resolvedDate = null;
            if (clean.contains("आज")) {
                resolvedDate = today.toString();
            } else if (clean.contains("कल")) {
                resolvedDate = today.plusDays(1).toString();
            } else if (clean.contains("परसों")) {
                resolvedDate = today.plusDays(2).toString();
            } else {
                resolvedDate = speechFormatter.resolveWeekdayFromText(text);
            }

            if (resolvedDate != null) {
                result.setSuccess(true);
                result.setDate(resolvedDate);
                result.setIntent("PROVIDE_INFO");
                return result;
            }
        }

        // 3. TIME
        if ("time".equals(state.getLastAskedField())) {
            String extractedTime = extractTimePattern(text);
            if (extractedTime != null) {
                result.setSuccess(true);
                result.setTime(extractedTime);
                result.setIntent("PROVIDE_INFO");
                return result;
            }
        }

        // 4. NAME
        if ("name".equals(state.getLastAskedField())) {
            String[] words = text.trim().split("\\s+");
            if (words.length <= 2 && inputValidatorService.isValidInput(text, "name")) {
                String name = text.replaceAll("[।.,!?]+", "").trim();
                boolean hasInvalid = false;
                String lowerName = name.toLowerCase();
                for (String bad : INVALID_NAME_TOKENS) {
                    if (lowerName.contains(bad)) {
                        hasInvalid = true;
                        break;
                    }
                }
                if (!hasInvalid && name.length() >= 2) {
                    result.setSuccess(true);
                    result.setName(name);
                    result.setIntent("PROVIDE_INFO");
                    return result;
                }
            }
        }

        return result;
    }

    private String extractTimePattern(String text) {
        String clean = text.toLowerCase().trim();
        Pattern timePattern = Pattern.compile("(\\d{1,2})\\s*(?:[:.]\\s*(\\d{2}))?\\s*(am|pm|बजे)?");
        Matcher matcher = timePattern.matcher(clean);
        if (matcher.find()) {
            String hour = matcher.group(1);
            String min = matcher.group(2) != null ? matcher.group(2) : "00";
            String meridian = matcher.group(3);

            int h = Integer.parseInt(hour);
            if (h >= 1 && h <= 24) {
                if ("pm".equals(meridian) && h != 12) {
                    h += 12;
                } else if ("am".equals(meridian) && h == 12) {
                    h = 0;
                } else if ("बजे".equals(meridian)) {
                    if (h >= 1 && h <= 8) {
                        h += 12;
                    }
                } else if (meridian == null) {
                    if (clean.contains("सुबह") && h == 12) {
                        h = 0;
                    } else if ((clean.contains("दोपहर") || clean.contains("शाम") || clean.contains("रात")) && h != 12 && h < 12) {
                        h += 12;
                    } else if (h >= 1 && h <= 8) {
                        h += 12;
                    }
                }
                return String.format("%02d:%s", h, min);
            }
        }
        return null;
    }

    public static class ExtractionResult {
        private boolean success = false;
        private String name;
        private String department;
        private String date;
        private String time;
        private Boolean isConfirming;
        private Boolean isQuerying;
        private Boolean isOutOfScope;
        private String intent;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public Boolean getIsConfirming() { return isConfirming; }
        public void setIsConfirming(Boolean isConfirming) { this.isConfirming = isConfirming; }
        public Boolean getIsQuerying() { return isQuerying; }
        public void setIsQuerying(Boolean isQuerying) { this.isQuerying = isQuerying; }
        public Boolean getIsOutOfScope() { return isOutOfScope; }
        public void setIsOutOfScope(Boolean isOutOfScope) { this.isOutOfScope = isOutOfScope; }
        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
    }
}
