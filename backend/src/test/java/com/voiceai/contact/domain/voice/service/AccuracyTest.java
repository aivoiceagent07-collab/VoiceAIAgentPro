package com.voiceai.contact.domain.voice.service;

import com.voiceai.contact.domain.voice.client.GroqClient;
import com.voiceai.contact.domain.voice.client.SarvamClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "SARVAM_API_KEY=dummy-sarvam-key-12345",
    "GROQ_API_KEY=dummy-groq-key-67890"
})
public class AccuracyTest {

    @Autowired
    private DepartmentRoutingService departmentRoutingService;

    @MockBean
    private SarvamClient sarvamClient;

    @MockBean
    private GroqClient groqClient;

    @Test
    public void testDepartmentRoutingAccuracy() {
        assertRoute("पेट में दर्द", "General Physician");
        assertRoute("गैस की समस्या", "General Physician");
        assertRoute("खुजली", "Dermatology");
        assertRoute("सीने में दर्द", "Cardiology");
        assertRoute("घुटने में दर्द", "Orthopedic");
        assertRoute("सिर दर्द", "General Physician");
        assertRoute("बच्चे को बुखार", "Pediatrics");
    }

    private void assertRoute(String input, String expectedDept) {
        DepartmentRoutingService.DepartmentRoutingResult result = departmentRoutingService.routeWithDetails(input);
        assertNotNull(result, "Routing result should not be null for input: " + input);
        assertEquals(expectedDept, result.getDepartment(), "Symptom '" + input + "' was routed incorrectly.");
        assertTrue(result.getConfidence() >= 0.8, "Confidence for symptom '" + input + "' should be high.");
        assertNotEquals("LLM Fallback", result.getLayer(), "Symptom '" + input + "' should be routed deterministically without LLM fallback.");
    }
}
