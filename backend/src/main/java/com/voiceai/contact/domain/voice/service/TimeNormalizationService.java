package com.voiceai.contact.domain.voice.service;

import org.springframework.stereotype.Service;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TimeNormalizationService {

    public LocalTime parseTimeToLocalTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String clean = raw.toLowerCase().trim();

        // 1. Determine meridian / period based on original text
        boolean isPm = false;
        boolean isAm = false;
        if (clean.contains("pm") || clean.contains("दोपहर") || clean.contains("शाम") || clean.contains("रात")) {
            isPm = true;
        }
        if (clean.contains("am") || clean.contains("सुबह")) {
            isAm = true;
        }

        // 2. Remove period words to prevent substring collisions (e.g., "दो" in "दोपहर")
        clean = clean
            .replace("दोपहर", "")
            .replace("सुबह", "")
            .replace("शाम", "")
            .replace("रात", "");

        // 3. Convert Hindi number words to standard digits
        clean = clean
            .replace("शून्य", "0")
            .replace("एक", "1")
            .replace("दो", "2")
            .replace("तीन", "3")
            .replace("चार", "4")
            .replace("पाँच", "5")
            .replace("पांच", "5")
            .replace("छह", "6")
            .replace("छः", "6")
            .replace("सात", "7")
            .replace("आठ", "8")
            .replace("नौ", "9")
            .replace("दस", "10")
            .replace("ग्यारह", "11")
            .replace("बारह", "12");

        // 4. Convert Hindi Devanagari digits to standard digits
        clean = clean
            .replace("०", "0")
            .replace("१", "1")
            .replace("२", "2")
            .replace("३", "3")
            .replace("४", "4")
            .replace("५", "5")
            .replace("६", "6")
            .replace("७", "7")
            .replace("८", "8")
            .replace("९", "9");

        // 5. Extract hours and minutes using regex
        Pattern timePattern = Pattern.compile("(\\d{1,2})(?:\\s*[:.]\\s*(\\d{2}))?");
        Matcher matcher = timePattern.matcher(clean);

        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;

            if (hour < 0 || hour > 24) {
                return null;
            }

            // Adjust hour for AM/PM
            if (isPm) {
                if (hour == 12) {
                    if (raw.toLowerCase().contains("रात")) {
                        hour = 0;
                    }
                } else if (hour < 12) {
                    hour += 12;
                }
            } else if (isAm) {
                if (hour == 12) {
                    hour = 0;
                }
            } else {
                // Heuristic for default clinic hours
                if (hour >= 1 && hour <= 8) {
                    hour += 12;
                }
            }

            return LocalTime.of(hour, minute);
        }

        return null;
    }

    public String toNaturalTime(LocalTime time) {
        if (time == null) return "";
        int hour24 = time.getHour();
        int minute = time.getMinute();
        int h12 = hour24 == 0 ? 12 : (hour24 > 12 ? hour24 - 12 : hour24);
        
        String prefix;
        if      (hour24 >= 5  && hour24 < 12) prefix = "सुबह";
        else if (hour24 == 12)                prefix = "दोपहर";
        else if (hour24 >= 12 && hour24 < 17) prefix = "दोपहर";
        else if (hour24 >= 17 && hour24 < 21) prefix = "शाम";
        else                                  prefix = "रात";

        String hindiNum = toHindiNumber(h12);
        if (minute == 0) {
            return prefix + " " + hindiNum + " बजे";
        } else {
            return prefix + " " + h12 + ":" + String.format("%02d", minute) + " बजे";
        }
    }

    public int parseToMinutes(String timeStr) {
        if (timeStr == null) return -1;
        String s = timeStr.trim().toUpperCase();
        try {
            if (s.contains(":")) {
                String[] parts = s.replace("AM", "").replace("PM", "").trim().split(":");
                int h = Integer.parseInt(parts[0].trim());
                int m = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                if (s.contains("PM") && h != 12) h += 12;
                if (s.contains("AM") && h == 12) h = 0;
                return h * 60 + m;
            } else {
                String num = s.replace("AM", "").replace("PM", "").replace("बजे", "").trim();
                num = num.replaceAll("[^0-9]", "");
                if (num.isEmpty()) return -1;
                int h = Integer.parseInt(num);
                if (s.contains("PM") && h != 12) h += 12;
                if (s.contains("AM") && h == 12) h = 0;
                return h * 60;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private String toHindiNumber(int n) {
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
}
