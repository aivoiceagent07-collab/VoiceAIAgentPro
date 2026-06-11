package com.voiceai.contact.domain.voice.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DepartmentRoutingService {

    private static final Map<String, List<String>> DEPT_KEYWORDS = new HashMap<>();

    static {
        DEPT_KEYWORDS.put("Cardiology", List.of(
            "chest pain", "heart", "cardio", "dil", "chhati", "sans", "seena", "heart rate", 
            "palpitation", "high bp", "blood pressure", "दिल", "छाती", "सांस", "सीना", "धड़कन"
        ));
        DEPT_KEYWORDS.put("Dermatology", List.of(
            "khujli", "rash", "skin", "derma", "tvacha", "khaj", "dane", "pimples", "eczema", 
            "itching", "acne", "पिंपल", "खुजली", "त्वचा", "खाज", "दाने", "त्वचा रोग"
        ));
        DEPT_KEYWORDS.put("Neurology", List.of(
            "neuro", "brain", "dimag", "sirdard", "mirgi", "headache", "migraine", "fits", 
            "stroke", "paralysis", "migrain", "दिमाग", "सिरदर्द", "मिर्गी", "दौरा", "पक्षाघात"
        ));
        DEPT_KEYWORDS.put("Orthopedic", List.of(
            "ortho", "haddi", "bone", "orthopedic", "asthi", "ghutne", "jod", "kamar", "banh", 
            "tang", "joint pain", "back pain", "knee pain", "fracture", "haddi dard", 
            "हड्डी", "अस्थि", "घुटने", "जोड़", "कमर", "बाँह्", "टाँग", "पीठ दर्द", "जोड़ों का दर्द"
        ));
        DEPT_KEYWORDS.put("Pediatrics", List.of(
            "paed", "pedia", "child", "bachha", "shishu", "infant", "pediatrician", "kids", 
            "bachon ka doctor", "बच्च", "शिशु", "बाल रोग", "खिलौना"
        ));
        DEPT_KEYWORDS.put("General Physician", List.of(
            "general", "samanya", "gp", "bukhar", "jukam", "fever", "cold", "cough", 
            "vomiting", "stomach ache", "weakness", "physician", "khansi", 
            "सामान्य", "बुखार", "जुकाम", "खांसी", "उल्टी", "पेट दर्द", "कमजोरी"
        ));
    }

    public String route(String transcription) {
        if (transcription == null || transcription.trim().isEmpty()) {
            return null;
        }

        String input = transcription.toLowerCase().trim();

        // Layer 1: Direct Keyword Matching
        for (Map.Entry<String, List<String>> entry : DEPT_KEYWORDS.entrySet()) {
            String dept = entry.getKey();
            for (String kw : entry.getValue()) {
                if (input.contains(kw)) {
                    System.out.println("[Dept Routing] Layer 1 Match found: '" + kw + "' -> " + dept);
                    return dept;
                }
            }
        }

        // Layer 2: Semantic Symptom Similarity Matching (Jaccard Similarity)
        String bestDept = null;
        double maxScore = 0.0;
        double threshold = 0.15;

        Set<String> inputWords = getWordSet(input);

        for (Map.Entry<String, List<String>> entry : DEPT_KEYWORDS.entrySet()) {
            String dept = entry.getKey();
            Set<String> deptWords = new HashSet<>();
            for (String kw : entry.getValue()) {
                deptWords.addAll(getWordSet(kw));
            }

            double score = calculateJaccardSimilarity(inputWords, deptWords);
            if (score > maxScore) {
                maxScore = score;
                bestDept = dept;
            }
        }

        if (maxScore >= threshold) {
            System.out.println("[Dept Routing] Layer 2 Similarity Match: " + bestDept + " (score=" + maxScore + ")");
            return bestDept;
        }

        return null;
    }

    private Set<String> getWordSet(String text) {
        Set<String> words = new HashSet<>();
        String[] tokens = text.replaceAll("[\\p{Punct}]+", "").split("\\s+");
        for (String t : tokens) {
            if (t.length() > 2) {
                words.add(t);
            }
        }
        return words;
    }

    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }
}
