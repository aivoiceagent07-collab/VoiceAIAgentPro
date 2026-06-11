package com.voiceai.contact.domain.voice.service;

import org.springframework.stereotype.Service;

@Service
public class DepartmentNormalizationService {

    public String normalize(String input) {
        if (input == null) return null;
        String s = input.toLowerCase().trim();

        // Cardiology
        if (s.contains("cardiologist") || s.contains("cardiology doctor") || s.contains("heart doctor") || s.contains("दिल के डॉक्टर") || s.contains("दिल का डॉक्टर")) {
            return "Cardiology";
        }

        // Orthopedic
        if (s.contains("orthopedic doctor") || s.contains("bone doctor") || s.contains("हड्डी डॉक्टर") || s.contains("हड्डी का डॉक्टर")) {
            return "Orthopedic";
        }

        // Neurology
        if (s.contains("neurologist") || s.contains("brain doctor") || s.contains("दिमाग का डॉक्टर") || s.contains("दिमाग के डॉक्टर")) {
            return "Neurology";
        }

        // Dermatology
        if (s.contains("dermatologist") || s.contains("skin doctor") || s.contains("त्वचा का डॉक्टर") || s.contains("त्वचा के डॉक्टर")) {
            return "Dermatology";
        }

        // Pediatrics
        if (s.contains("pediatrician") || s.contains("child doctor") || s.contains("बच्चों का डॉक्टर") || s.contains("बच्चों के डॉक्टर")) {
            return "Pediatrics";
        }

        // General Physician
        if (s.contains("general doctor") || s.contains("सामान्य डॉक्टर")) {
            return "General Physician";
        }

        return null;
    }
}
