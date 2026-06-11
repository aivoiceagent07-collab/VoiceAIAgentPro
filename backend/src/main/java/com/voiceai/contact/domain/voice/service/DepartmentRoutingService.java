package com.voiceai.contact.domain.voice.service;

import com.voiceai.contact.domain.voice.client.GroqClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DepartmentRoutingService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentRoutingService.class);

    private final GroqClient groqClient;

    // Layer 1: Exact Symptom Maps
    private static final Map<String, String> EXACT_SYMPTOMS = new LinkedHashMap<>();

    // Layer 2: Keyword Scoring Map
    private static final Map<String, Map<String, Integer>> KEYWORD_SCORES = new HashMap<>();

    // Layer 3: Department Keywords for Jaccard Similarity
    private static final Map<String, List<String>> DEPT_KEYWORDS = new HashMap<>();

    static {
        // Populating Exact Symptoms (Layer 1) - Sorted by length descending to match longest first
        Map<String, String> rawExact = new HashMap<>();
        
        // General Physician
        rawExact.put("सिर में दर्द", "General Physician");
        rawExact.put("सर में दर्द", "General Physician");
        rawExact.put("सिर दर्द", "General Physician");
        rawExact.put("सर दर्द", "General Physician");
        rawExact.put("बुखार", "General Physician");
        rawExact.put("उल्टी", "General Physician");
        rawExact.put("उल्टी हो रही है", "General Physician");
        rawExact.put("पेट में दर्द", "General Physician");
        rawExact.put("पेट दर्द", "General Physician");
        rawExact.put("गैस की समस्या", "General Physician");
        rawExact.put("गैस", "General Physician");
        rawExact.put("कमजोरी", "General Physician");
        rawExact.put("headache", "General Physician");
        rawExact.put("stomach pain", "General Physician");
        rawExact.put("stomach ache", "General Physician");
        rawExact.put("gas problem", "General Physician");
        rawExact.put("bukhar", "General Physician");
        rawExact.put("जुकाम", "General Physician");
        rawExact.put("acidity", "General Physician");
        rawExact.put("stomach", "General Physician");
        rawExact.put("gas", "General Physician");
        rawExact.put("cold", "General Physician");
        rawExact.put("cough", "General Physician");
        rawExact.put("vomiting", "General Physician");
        rawExact.put("samanya", "General Physician");

        // Orthopedic
        rawExact.put("पैर में दर्द", "Orthopedic");
        rawExact.put("पैर दर्द", "Orthopedic");
        rawExact.put("घुटना दर्द", "Orthopedic");
        rawExact.put("घुटने में दर्द", "Orthopedic");
        rawExact.put("घुटने दर्द", "Orthopedic");
        rawExact.put("हड्डी दर्द", "Orthopedic");
        rawExact.put("कमर दर्द", "Orthopedic");
        rawExact.put("पीठ दर्द", "Orthopedic");
        rawExact.put("हाथ दर्द", "Orthopedic");
        rawExact.put("कंधा दर्द", "Orthopedic");
        rawExact.put("फ्रैक्चर", "Orthopedic");
        rawExact.put("knee pain", "Orthopedic");
        rawExact.put("joint pain", "Orthopedic");
        rawExact.put("haddi dard", "Orthopedic");
        rawExact.put("back pain", "Orthopedic");
        rawExact.put("knee", "Orthopedic");
        rawExact.put("joint", "Orthopedic");
        rawExact.put("bone", "Orthopedic");
        rawExact.put("fracture", "Orthopedic");

        // Cardiology
        rawExact.put("सीने में दर्द", "Cardiology");
        rawExact.put("दिल की धड़कन", "Cardiology");
        rawExact.put("सांस फूलना", "Cardiology");
        rawExact.put("chest pain", "Cardiology");
        rawExact.put("heart pain", "Cardiology");
        rawExact.put("chest", "Cardiology");
        rawExact.put("heart", "Cardiology");
        rawExact.put("cardio", "Cardiology");

        // Dermatology
        rawExact.put("खुजली", "Dermatology");
        rawExact.put("रैश", "Dermatology");
        rawExact.put("त्वचा समस्या", "Dermatology");
        rawExact.put("skin problem", "Dermatology");
        rawExact.put("itching", "Dermatology");
        rawExact.put("dane", "Dermatology");
        rawExact.put("pimples", "Dermatology");
        rawExact.put("पिंपल", "Dermatology");
        rawExact.put("skin", "Dermatology");
        rawExact.put("त्वचा की समस्या", "Dermatology");
        rawExact.put("त्वचा", "Dermatology");

        // Neurology
        rawExact.put("माइग्रेन", "Neurology");
        rawExact.put("चक्कर", "Neurology");
        rawExact.put("बेहोशी", "Neurology");
        rawExact.put("mirgi", "Neurology");
        rawExact.put("मिर्गी", "Neurology");
        rawExact.put("brain", "Neurology");
        rawExact.put("neuro", "Neurology");

        // Pediatrics
        rawExact.put("बच्चे को बुखार", "Pediatrics");
        rawExact.put("बच्चे की खांसी", "Pediatrics");
        rawExact.put("बच्चे की खाँसी", "Pediatrics");
        rawExact.put("bachha bukhar", "Pediatrics");
        rawExact.put("child fever", "Pediatrics");
        rawExact.put("child", "Pediatrics");
        rawExact.put("bachha", "Pediatrics");
        rawExact.put("kids", "Pediatrics");

        // Sort keys by length descending to match longest phrases first
        List<String> keys = new ArrayList<>(rawExact.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String k : keys) {
            EXACT_SYMPTOMS.put(k, rawExact.get(k));
        }

        // Layer 2: Keyword Scores setup
        // GP
        addScoreWord("पेट", "General Physician", 10);
        addScoreWord("stomach", "General Physician", 10);
        addScoreWord("abdominal", "General Physician", 10);
        addScoreWord("gas", "General Physician", 10);
        addScoreWord("acidity", "General Physician", 10);
        addScoreWord("बुखार", "General Physician", 10);
        addScoreWord("fever", "General Physician", 10);
        addScoreWord("jukam", "General Physician", 10);
        addScoreWord("cold", "General Physician", 10);
        addScoreWord("cough", "General Physician", 10);
        addScoreWord("vomiting", "General Physician", 10);
        addScoreWord("उल्टी", "General Physician", 10);
        addScoreWord("samanya", "General Physician", 10);
        addScoreWord("सिर", "General Physician", 10);
        addScoreWord("सर", "General Physician", 10);
        addScoreWord("sirdard", "General Physician", 10);
        addScoreWord("headache", "General Physician", 10);
        addScoreWord("head", "General Physician", 10);
        addScoreWord("कमजोरी", "General Physician", 10);

        // Cardiology
        addScoreWord("सीने", "Cardiology", 10);
        addScoreWord("chest", "Cardiology", 10);
        addScoreWord("heart", "Cardiology", 10);
        addScoreWord("cardio", "Cardiology", 10);
        addScoreWord("dil", "Cardiology", 10);
        addScoreWord("दिल", "Cardiology", 10);
        addScoreWord("chhati", "Cardiology", 10);
        addScoreWord("seena", "Cardiology", 10);
        addScoreWord("धड़कन", "Cardiology", 10);
        addScoreWord("bp", "Cardiology", 10);
        addScoreWord("सांस", "Cardiology", 10);

        // Dermatology
        addScoreWord("खुजली", "Dermatology", 10);
        addScoreWord("itching", "Dermatology", 10);
        addScoreWord("skin", "Dermatology", 10);
        addScoreWord("derma", "Dermatology", 10);
        addScoreWord("tvacha", "Dermatology", 10);
        addScoreWord("त्वचा", "Dermatology", 10);
        addScoreWord("dane", "Dermatology", 10);
        addScoreWord("pimples", "Dermatology", 10);
        addScoreWord("पिंपल", "Dermatology", 10);
        addScoreWord("रैश", "Dermatology", 10);

        // Orthopedic
        addScoreWord("घुटना", "Orthopedic", 10);
        addScoreWord("घुटने", "Orthopedic", 10);
        addScoreWord("knee", "Orthopedic", 10);
        addScoreWord("bone", "Orthopedic", 10);
        addScoreWord("ortho", "Orthopedic", 10);
        addScoreWord("joint", "Orthopedic", 10);
        addScoreWord("haddi", "Orthopedic", 10);
        addScoreWord("हड्डी", "Orthopedic", 10);
        addScoreWord("jod", "Orthopedic", 10);
        addScoreWord("kamar", "Orthopedic", 10);
        addScoreWord("कमर", "Orthopedic", 10);
        addScoreWord("back", "Orthopedic", 10);
        addScoreWord("पीठ", "Orthopedic", 10);
        addScoreWord("पैर", "Orthopedic", 10);
        addScoreWord("हाथ", "Orthopedic", 10);
        addScoreWord("कंधा", "Orthopedic", 10);
        addScoreWord("फ्रैक्चर", "Orthopedic", 10);
        addScoreWord("fracture", "Orthopedic", 10);

        // Pediatrics
        addScoreWord("बच्चे", "Pediatrics", 10);
        addScoreWord("बच्चा", "Pediatrics", 10);
        addScoreWord("child", "Pediatrics", 10);
        addScoreWord("pedia", "Pediatrics", 10);
        addScoreWord("paed", "Pediatrics", 10);
        addScoreWord("kids", "Pediatrics", 10);
        addScoreWord("bachha", "Pediatrics", 10);
        addScoreWord("shishu", "Pediatrics", 10);
        addScoreWord("शिशु", "Pediatrics", 10);
        addScoreWord("खांसी", "Pediatrics", 10);
        addScoreWord("खाँसी", "Pediatrics", 10);

        // Neurology
        addScoreWord("दिमाग", "Neurology", 10);
        addScoreWord("brain", "Neurology", 10);
        addScoreWord("neuro", "Neurology", 10);
        addScoreWord("mirgi", "Neurology", 10);
        addScoreWord("मिर्गी", "Neurology", 10);
        addScoreWord("fits", "Neurology", 10);
        addScoreWord("stroke", "Neurology", 10);
        addScoreWord("paralysis", "Neurology", 10);
        addScoreWord("माइग्रेन", "Neurology", 10);
        addScoreWord("चक्कर", "Neurology", 10);
        addScoreWord("बेहोशी", "Neurology", 10);

        // Layer 3 Setup - keywords for Jaccard Similarity
        DEPT_KEYWORDS.put("Cardiology", List.of(
            "chest pain", "heart", "cardio", "dil", "chhati", "sans", "seena", "heart rate", 
            "palpitation", "high bp", "blood pressure", "दिल", "छाती", "सांस", "सीना", "धड़कन"
        ));
        DEPT_KEYWORDS.put("Dermatology", List.of(
            "khujli", "rash", "skin", "derma", "tvacha", "khaj", "dane", "pimples", "eczema", 
            "itching", "acne", "पिंपल", "खुजली", "त्वचा", "खाज", "दाने", "त्वचा रोग", "रैश"
        ));
        DEPT_KEYWORDS.put("Neurology", List.of(
            "neuro", "brain", "dimag", "sirdard", "mirgi", "headache", "migraine", "fits", 
            "stroke", "paralysis", "migrain", "दिमाग", "सिरदर्द", "मिर्गी", "दौरा", "पक्षाघात", "माइग्रेन", "चक्कर", "बेहोशी"
        ));
        DEPT_KEYWORDS.put("Orthopedic", List.of(
            "ortho", "haddi", "bone", "orthopedic", "asthi", "ghutne", "jod", "kamar", "banh", 
            "tang", "joint pain", "back pain", "knee pain", "fracture", "haddi dard", 
            "हड्डी", "अस्थि", "घुटने", "जोड़", "कमर", "बाँह्", "टाँग", "पीठ दर्द", "जोड़ों का दर्द", "पैर", "हाथ", "कंधा", "फ्रैक्चर"
        ));
        DEPT_KEYWORDS.put("Pediatrics", List.of(
            "paed", "pedia", "child", "bachha", "shishu", "infant", "pediatrician", "kids", 
            "bachon ka doctor", "बच्च", "शिशु", "बाल रोग", "खिलौना", "खांसी", "खाँसी"
        ));
        DEPT_KEYWORDS.put("General Physician", List.of(
            "general", "samanya", "gp", "bukhar", "jukam", "fever", "cold", "cough", 
            "vomiting", "stomach ache", "weakness", "physician", "khansi", 
            "सामान्य", "बुखार", "जुकाम", "खांसी", "उल्टी", "पेट दर्द", "कमजोरी", "सिर दर्द", "सर दर्द"
        ));
    }

    private static void addScoreWord(String word, String dept, int score) {
        KEYWORD_SCORES.computeIfAbsent(word, k -> new HashMap<>()).put(dept, score);
    }

    public DepartmentRoutingService(@Lazy GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    public String route(String transcription) {
        DepartmentRoutingResult result = routeWithDetails(transcription);
        if (result != null) {
            log.info("[ROUTING] Department={}, Source={}, Confidence={}", 
                     result.getDepartment(), result.getLayer(), result.getConfidence());
            return result.getDepartment();
        }
        return null;
    }

    public DepartmentRoutingResult routeWithDetails(String transcription) {
        if (transcription == null || transcription.trim().isEmpty()) {
            return null;
        }

        String clean = transcription.toLowerCase().trim();

        // Layer 1: Exact Symptom Mapping
        for (Map.Entry<String, String> entry : EXACT_SYMPTOMS.entrySet()) {
            String symptom = entry.getKey();
            String dept = entry.getValue();
            if (clean.contains(symptom)) {
                return new DepartmentRoutingResult(dept, "Exact Symptom Match", 1.0);
            }
        }

        // Layer 2: Keyword Scoring
        Map<String, Integer> deptScores = new HashMap<>();
        String[] tokens = clean.replaceAll("[\\p{Punct}]+", "").split("\\s+");
        for (String token : tokens) {
            // Ignore generic keywords that carry no routing signal
            if (token.equals("दर्द") || token.equals("problem") || token.equals("issue") || token.equals("समस्या")) {
                continue;
            }
            Map<String, Integer> scores = KEYWORD_SCORES.get(token);
            if (scores != null) {
                for (Map.Entry<String, Integer> scoreEntry : scores.entrySet()) {
                    deptScores.put(scoreEntry.getKey(), deptScores.getOrDefault(scoreEntry.getKey(), 0) + scoreEntry.getValue());
                }
            }
        }

        String bestScoreDept = null;
        int maxScore = 0;
        for (Map.Entry<String, Integer> entry : deptScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestScoreDept = entry.getKey();
            }
        }

        if (bestScoreDept != null && maxScore >= 10) {
            return new DepartmentRoutingResult(bestScoreDept, "Keyword Score Match", 0.97);
        }

        // Layer 3: Semantic Jaccard Similarity Match
        String bestSimilarityDept = null;
        double maxSimilarity = 0.0;
        double similarityThreshold = 0.15;

        Set<String> inputWords = getWordSet(clean);

        for (Map.Entry<String, List<String>> entry : DEPT_KEYWORDS.entrySet()) {
            String dept = entry.getKey();
            Set<String> deptWords = new HashSet<>();
            for (String kw : entry.getValue()) {
                deptWords.addAll(getWordSet(kw));
            }

            double score = calculateJaccardSimilarity(inputWords, deptWords);
            if (score > maxSimilarity) {
                maxSimilarity = score;
                bestSimilarityDept = dept;
            }
        }

        if (maxSimilarity >= similarityThreshold) {
            return new DepartmentRoutingResult(bestSimilarityDept, "Semantic Similarity Match", 0.80);
        }

        // Layer 4: LLM Fallback (confidence is below local heuristics threshold)
        try {
            String llmDept = groqClient.classifyDepartment(transcription);
            if (llmDept != null) {
                return new DepartmentRoutingResult(llmDept, "LLM Fallback", 0.70);
            }
        } catch (Exception e) {
            log.error("Layer 4 Fallback classification failed: {}", e.getMessage());
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

    public static class DepartmentRoutingResult {
        private final String department;
        private final String layer;
        private final double confidence;

        public DepartmentRoutingResult(String department, String layer, double confidence) {
            this.department = department;
            this.layer = layer;
            this.confidence = confidence;
        }

        public String getDepartment() { return department; }
        public String getLayer() { return layer; }
        public double getConfidence() { return confidence; }
    }
}
