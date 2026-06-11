package com.voiceai.contact.domain.voice.service;

import com.voiceai.contact.domain.voice.config.ClinicConfig;
import com.voiceai.contact.domain.voice.model.SessionState;
import com.voiceai.contact.domain.voice.util.SpeechFormatter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ClinicService {

    private final SpeechFormatter speechFormatter;
    private final TimeNormalizationService timeNormalizationService;

    public ClinicService(SpeechFormatter speechFormatter, TimeNormalizationService timeNormalizationService) {
        this.speechFormatter = speechFormatter;
        this.timeNormalizationService = timeNormalizationService;
    }

    public String matchDoctorRaw(SessionState state) {
        if (state.getDepartment() == null) return "Any available doctor";
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.toLowerCase().contains(state.getDepartment().toLowerCase()) || state.getDepartment().toLowerCase().contains(dept.toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        if (matchedDept == null) return "General Doctor";
        String dayOfWeek = speechFormatter.getDayOfWeek(state.getDate());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors != null) {
            for (Map<String, Object> doc : doctors) {
                @SuppressWarnings("unchecked")
                List<String> days = (List<String>) doc.get("days");
                if (dayOfWeek != null && days.contains(dayOfWeek)) {
                    return (String) doc.get("name");
                }
            }
            if (!doctors.isEmpty()) {
                return (String) doctors.get(0).get("name");
            }
        }
        return "General Doctor";
    }

    public String buildAvailabilityResponse(SessionState state) {
        if (state.getDepartment() == null) {
            return "विभाग नहीं बताया गया।";
        }
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.equalsIgnoreCase(state.getDepartment()) || dept.toLowerCase().contains(state.getDepartment().toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        if (matchedDept == null) return "इस विभाग के लिए कोई डॉक्टर नहीं मिला।";

        String dayOfWeek = speechFormatter.getDayOfWeek(state.getDate());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors == null) return "इस विभाग में अभी कोई डॉक्टर उपलब्ध नहीं हैं।";

        for (Map<String, Object> doc : doctors) {
            @SuppressWarnings("unchecked")
            List<String> days = (List<String>) doc.get("days");
            if (dayOfWeek != null && !days.contains(dayOfWeek)) continue;

            String startH   = speechFormatter.toNaturalTime((String) doc.get("start"));
            String endH     = speechFormatter.toNaturalTime((String) doc.get("end"));
            String dayHindi = speechFormatter.toHindiDay(dayOfWeek != null ? dayOfWeek : ((List<String>) doc.get("days")).get(0));
            String docDisplay = speechFormatter.formatDoctorName(doc.get("name").toString());
            return dayHindi + " को " + docDisplay + " " + startH + " से " + endH + " तक उपलब्ध हैं।";
        }
        return "इस दिन कोई डॉक्टर उपलब्ध नहीं हैं।";
    }

    public boolean isTimeInSlot(java.time.LocalTime time, SessionState state) {
        if (time == null || state.getDepartment() == null || state.getDate() == null) return true;
        String dayOfWeek = speechFormatter.getDayOfWeek(state.getDate());
        if (dayOfWeek == null) return true;
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.equalsIgnoreCase(state.getDepartment()) || dept.toLowerCase().contains(state.getDepartment().toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        if (matchedDept == null) return true;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors == null) return true;
        for (Map<String, Object> doc : doctors) {
            @SuppressWarnings("unchecked")
            List<String> days = (List<String>) doc.get("days");
            if (!days.contains(dayOfWeek)) continue;
            try {
                int slotStart = timeNormalizationService.parseToMinutes((String) doc.get("start"));
                int slotEnd   = timeNormalizationService.parseToMinutes((String) doc.get("end"));
                int requested = time.getHour() * 60 + time.getMinute();
                return requested >= slotStart && requested <= slotEnd;
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    public Map<String, Object> getWorkingDoctor(SessionState state) {
        if (state.getDepartment() == null || state.getDate() == null) return null;
        String dayOfWeek = speechFormatter.getDayOfWeek(state.getDate());
        if (dayOfWeek == null) return null;
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.equalsIgnoreCase(state.getDepartment()) || dept.toLowerCase().contains(state.getDepartment().toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        if (matchedDept == null) return null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors == null) return null;
        for (Map<String, Object> doc : doctors) {
            @SuppressWarnings("unchecked")
            List<String> days = (List<String>) doc.get("days");
            if (days.contains(dayOfWeek)) {
                return doc;
            }
        }
        return null;
    }
}
