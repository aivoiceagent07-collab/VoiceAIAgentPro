package com.voiceai.contact.service;

import com.voiceai.contact.model.SessionState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class SpeechFormatter {

    private static final Map<String, String> DEPT_PHONETICS = new LinkedHashMap<>() {{
        put("General Physician", "जनरल फिजिशियन");
        put("Orthopedic",        "ऑर्थोपेडिक");
        put("Neurology",         "न्यूरोलॉजी");
        put("Cardiology",        "कार्डियोलॉजी");
        put("Dermatology",       "डर्मेटोलॉजी");
        put("Pediatrics",        "पीडियाट्रिक्स");
    }};

    private static final Map<String, String> DOCTOR_PHONETICS = new LinkedHashMap<>() {{
        put("Iyer",      "आयर");
        put("Roy",       "रॉय");
        put("Khan",      "खान");
        put("Sharma",    "शर्मा");
        put("Verma",     "वर्मा");
        put("Nair",      "नायर");
        put("Gupta",     "गुप्ता");
        put("Das",       "दास");
        put("Reddy",     "रेड्डी");
        put("Pillai",    "पिल्लई");
        put("Kapoor",    "कपूर");
        put("Singh",     "सिंह");
        put("Ali",       "अली");
        put("Joshi",     "जोशी");
        put("Kumar",     "कुमार");
        put("Thomas",    "थॉमस");
        put("Patel",     "पटेल");
        put("Arora",     "अरोड़ा");
        put("Mishra",    "मिश्रा");
        put("Fernandes", "फर्नांडिस");
        put("Sen",       "सेन");
    }};

    public String toHindiNumber(int n) {
        return switch (n) {
            case 1  -> "एक";
            case 2  -> "दो";
            case 3  -> "तीन";
            case 4  -> "चार";
            case 5  -> "पाँच";
            case 6  -> "छह";
            case 7  -> "सात";
            case 8  -> "आठ";
            case 9  -> "नौ";
            case 10 -> "दस";
            case 11 -> "ग्यारह";
            case 12 -> "बारह";
            default -> String.valueOf(n);
        };
    }

    public String formatDeptName(String dept) {
        if (dept == null) return "";
        return DEPT_PHONETICS.getOrDefault(dept, dept);
    }

    public String formatDoctorName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String name = raw.trim().replaceFirst("^Dr\\.\\s*", "").trim();
        for (Map.Entry<String, String> e : DOCTOR_PHONETICS.entrySet()) {
            name = name.replace(e.getKey(), e.getValue());
        }
        return "डॉक्टर " + name;
    }

    public String resolveWeekdayFromText(String text) {
        if (text == null) return null;
        String low = text.toLowerCase().trim();

        Map<String, DayOfWeek> keywords = new LinkedHashMap<>();
        keywords.put("monday",      DayOfWeek.MONDAY);
        keywords.put("मंडे",         DayOfWeek.MONDAY);
        keywords.put("सोमवार",       DayOfWeek.MONDAY);
        keywords.put("tuesday",     DayOfWeek.TUESDAY);
        keywords.put("मंगलवार",      DayOfWeek.TUESDAY);
        keywords.put("wednesday",   DayOfWeek.WEDNESDAY);
        keywords.put("बुधवार",       DayOfWeek.WEDNESDAY);
        keywords.put("thursday",    DayOfWeek.THURSDAY);
        keywords.put("गुरुवार",      DayOfWeek.THURSDAY);
        keywords.put("friday",      DayOfWeek.FRIDAY);
        keywords.put("शुक्रवार",     DayOfWeek.FRIDAY);
        keywords.put("saturday",    DayOfWeek.SATURDAY);
        keywords.put("शनिवार",      DayOfWeek.SATURDAY);
        keywords.put("sunday",      DayOfWeek.SUNDAY);
        keywords.put("रविवार",      DayOfWeek.SUNDAY);

        for (Map.Entry<String, DayOfWeek> e : keywords.entrySet()) {
            if (low.contains(e.getKey())) {
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
                LocalDate target = DateNormalizerService.nextWeekday(today, e.getValue());
                System.out.println("[LOG] resolveWeekdayFromText: '" + e.getKey() + "' → " + target);
                return target.toString();
            }
        }
        return null;
    }

    public String toHindiDate(String isoDate) {
        if (isoDate == null) return "";
        try {
            String[] parts = isoDate.split("-");
            if (parts.length < 3) return isoDate;
            int day   = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[1]);
            String[] MONTHS_HI = {
                "", "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
                "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
            };
            String monthHi = (month >= 1 && month <= 12) ? MONTHS_HI[month] : parts[1];
            return day + " " + monthHi;
        } catch (Exception e) {
            return isoDate;
        }
    }

    public String toNaturalTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            int hour24;
            String up = raw.trim().toUpperCase();
            if (up.contains("AM") || up.contains("PM")) {
                String numPart = up.replaceAll("[^0-9:]", "").split(":")[0];
                int h = Integer.parseInt(numPart);
                if (up.contains("PM") && h != 12) h += 12;
                if (up.contains("AM") && h == 12) h = 0;
                hour24 = h;
            } else if (raw.contains(":")) {
                hour24 = Integer.parseInt(raw.split(":")[0].trim());
            } else {
                hour24 = Integer.parseInt(raw.replaceAll("[^0-9]", "").trim());
            }

            int h12 = hour24 == 0 ? 12 : (hour24 > 12 ? hour24 - 12 : hour24);
            String prefix;
            if      (hour24 >= 5  && hour24 < 12) prefix = "सुबह";
            else if (hour24 == 12)                 prefix = "दोपहर";
            else if (hour24 >= 12 && hour24 < 17)  prefix = "दोपहर";
            else if (hour24 >= 17 && hour24 < 21)  prefix = "शाम";
            else                                    prefix = "रात";

            return prefix + " " + toHindiNumber(h12) + " बजे";
        } catch (Exception e) {
            return raw;
        }
    }

    public String to12Hour(String hhmm) {
        if (hhmm == null) return "";
        try {
            String[] parts = hhmm.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (h == 0) h = 12;
            else if (h > 12) h -= 12;
            return m == 0 ? h + " बजे" : h + ":" + String.format("%02d", m) + " बजे";
        } catch (Exception e) {
            return hhmm;
        }
    }

    public String toHindiDay(String day) {
        if (day == null) return "";
        return switch (day) {
            case "Monday"    -> "सोमवार";
            case "Tuesday"   -> "मंगलवार";
            case "Wednesday" -> "बुधवार";
            case "Thursday"  -> "गुरुवार";
            case "Friday"    -> "शुक्रवार";
            case "Saturday"  -> "शनिवार";
            case "Sunday"    -> "रविवार";
            default          -> day;
        };
    }

    public String resolvePostConfirmQuery(String lowerInput, SessionState state) {
        String dayH  = toHindiDay(getDayOfWeek(state.getDate()));
        String timeN = toNaturalTime(state.getTime());
        String dept  = formatDeptName(state.getDepartment());
        String doc   = state.getAssignedDoctor() != null
                        ? formatDoctorName(state.getAssignedDoctor()) : "";

        if (lowerInput.contains("विभाग") || lowerInput.contains("department")
                || lowerInput.contains("किस में") || lowerInput.contains("कौन सा विभाग")) {
            return "जी, आपका अपॉइंटमेंट " + dept + " विभाग में है।";
        }
        if (lowerInput.contains("कब") || lowerInput.contains("कौन सा दिन")
                || lowerInput.contains("तारीख") || lowerInput.contains("date")
                || lowerInput.contains("दिन")) {
            return "आपका अपॉइंटमेंट " + dayH + " को है।";
        }
        if (lowerInput.contains("समय") || lowerInput.contains("टाइम")
                || lowerInput.contains("time") || lowerInput.contains("बजे")) {
            return "आपका अपॉइंटमेंट " + timeN + " का है।";
        }
        if (lowerInput.contains("डॉक्टर") || lowerInput.contains("doctor")
                || lowerInput.contains("कौन") || lowerInput.contains("डॉ")) {
            return "आपका अपॉइंटमेंट " + doc + " के साथ है।";
        }
        return null;
    }

    public String getDayOfWeek(String dateStr) {
        if (dateStr == null) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr);
            String day = date.getDayOfWeek().name();
            return day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}
