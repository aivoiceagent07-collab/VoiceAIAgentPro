package com.voiceai.contact.domain.voice.service;

import com.voiceai.contact.domain.voice.client.GroqClient;
import com.voiceai.contact.domain.voice.client.SarvamClient;
import com.voiceai.contact.domain.voice.dto.LlmExtractionResponse;
import com.voiceai.contact.domain.voice.dto.VoiceResponse;
import com.voiceai.contact.domain.voice.model.SessionState;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {
    "SARVAM_API_KEY=dummy-sarvam-key-12345",
    "GROQ_API_KEY=dummy-groq-key-67890"
})
public class AccuracyTest {

    @Autowired
    private DepartmentRoutingService departmentRoutingService;

    @Autowired
    private VoiceService voiceService;

    @Autowired
    private SessionManagerService sessionManagerService;

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

    @Test
    public void testScenario1_SymptomGP_Suggest_Confirm_GoesToDate() throws Exception {
        String sessionId = "test-scenario-1";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        // Turn 1: User says symptom "मेरे पेट में दर्द है"
        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("मेरे पेट में दर्द है");
        LlmExtractionResponse ext1 = new LlmExtractionResponse();
        ext1.setIntent("PROVIDE_INFO");
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext1);
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

        VoiceResponse resp1 = voiceService.processVoice(mockFile, state.getSessionId());

        assertNull(state.getDepartment());
        assertEquals("General Physician", state.getSuggestedDepartment());
        assertTrue(state.getSuggestedDeptConfidence() >= 0.95);
        assertEquals("department_suggestion", state.getLastAskedField());
        assertTrue(resp1.getText().contains("जनरल फिजिशियन") && resp1.getText().contains("क्या मैं यही विभाग चुनूँ"));

        // Turn 2: User confirms with "हाँ"
        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("हाँ");

        VoiceResponse resp2 = voiceService.processVoice(mockFile, state.getSessionId());

        assertEquals("General Physician", state.getDepartment());
        assertNull(state.getSuggestedDepartment());
        assertEquals("date", state.getLastAskedField());
        assertTrue(resp2.getText().contains("किस दिन आना चाहेंगे"));
    }

    @Test
    public void testScenario2_SymptomGP_Suggest_NegateAndCorrection_SetsCardiology() throws Exception {
        String sessionId = "test-scenario-2";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        // Turn 1: User says symptom "मेरे पेट में दर्द है"
        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("मेरे पेट में दर्द है");
        LlmExtractionResponse ext1 = new LlmExtractionResponse();
        ext1.setIntent("PROVIDE_INFO");
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext1);
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

        VoiceResponse resp1 = voiceService.processVoice(mockFile, state.getSessionId());

        assertNull(state.getDepartment());
        assertEquals("General Physician", state.getSuggestedDepartment());
        assertEquals("department_suggestion", state.getLastAskedField());

        // Turn 2: User negates and corrects: "नहीं, कार्डियोलॉजी"
        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("नहीं, कार्डियोलॉजी");

        VoiceResponse resp2 = voiceService.processVoice(mockFile, state.getSessionId());

        assertEquals("Cardiology", state.getDepartment());
        assertNull(state.getSuggestedDepartment());
        assertEquals("date", state.getLastAskedField());
        assertTrue(resp2.getText().contains("किस दिन आना चाहेंगे"));
    }

    @Test
    public void testScenario3_SymptomDerm_SuggestsDerm_WaitsForResponse() throws Exception {
        String sessionId = "test-scenario-3";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        // Turn 1: User says "मुझे त्वचा की समस्या है"
        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("मुझे त्वचा की समस्या है");
        LlmExtractionResponse ext1 = new LlmExtractionResponse();
        ext1.setIntent("PROVIDE_INFO");
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext1);
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

        VoiceResponse resp1 = voiceService.processVoice(mockFile, state.getSessionId());

        assertNull(state.getDepartment());
        assertEquals("Dermatology", state.getSuggestedDepartment());
        assertEquals("department_suggestion", state.getLastAskedField());
        assertTrue(resp1.getText().contains("डर्मेटोलॉजी") && resp1.getText().contains("क्या मैं यही विभाग चुनूँ"));
    }

    @Test
    public void testScenario4_DirectExplicitCardiology_SetsCardiologyDirectly() throws Exception {
        String sessionId = "test-scenario-4";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        // Turn 1: User says "मुझे कार्डियोलॉजी में दिखाना है"
        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("मुझे कार्डियोलॉजी में दिखाना है");
        LlmExtractionResponse ext1 = new LlmExtractionResponse();
        ext1.setIntent("PROVIDE_INFO");
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext1);
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

        VoiceResponse resp1 = voiceService.processVoice(mockFile, state.getSessionId());

        // Assert department is immediately set to Cardiology, suggested department is cleared or never set
        assertEquals("Cardiology", state.getDepartment());
        assertNull(state.getSuggestedDepartment());
        assertEquals("date", state.getLastAskedField());
        assertTrue(resp1.getText().contains("किस दिन आना चाहेंगे"));
    }

    @Test
    public void testSymptomDoesNotTriggerOutOfScope() throws Exception {
        String sessionId = "test-scope-symptom";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("मेरे पेट में दर्द है");
        
        LlmExtractionResponse ext = new LlmExtractionResponse();
        ext.setIntent("PROVIDE_INFO");
        ext.setIsOutOfScope(true);
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext);
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

        VoiceResponse response = voiceService.processVoice(mockFile, state.getSessionId());

        assertEquals("General Physician", state.getSuggestedDepartment());
        assertEquals("department_suggestion", state.getLastAskedField());
        assertTrue(response.getText().contains("जनरल फिजिशियन"));
    }

    @Test
    public void testFillerDoesNotBecomeDepartment() throws Exception {
        String sessionId = "test-filler-dept";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setLastAskedField("department");
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        for (String filler : java.util.List.of("ठीक है", "हाँ", "नहीं", "टेस्ट", "नमस्ते", "ओके")) {
            Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn(filler);
            LlmExtractionResponse ext = new LlmExtractionResponse();
            ext.setIntent("PROVIDE_INFO");
            ext.setDepartment(filler);
            Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext);
            Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

            voiceService.processVoice(mockFile, state.getSessionId());

            assertNotEquals(filler, state.getDepartment());
            assertNull(state.getDepartment());
        }
    }

    @Test
    public void testConfirmationBlockedWithMissingSlots() throws Exception {
        String sessionId = "test-missing-slots";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        
        state.setPatientName("Rahul");
        state.setDepartment("Orthopedic");
        state.setDate(null);
        state.setTime(java.time.LocalTime.of(10, 0));
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("हाँ");
        LlmExtractionResponse ext = new LlmExtractionResponse();
        ext.setIntent("CONTINUE");
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext);
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

        VoiceResponse response = voiceService.processVoice(mockFile, state.getSessionId());

        assertNotEquals(SessionState.Mode.CONFIRMATION, state.getMode());
        assertEquals(SessionState.Mode.BOOKING, state.getMode());
        assertFalse(response.getText().contains("कन्फर्म"));
    }

    @Test
    public void testPastDateClearsDateSlot() throws Exception {
        String sessionId = "test-past-date";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setDepartment("Orthopedic");
        state.setDate("2026-06-15");
        state.setTime(java.time.LocalTime.of(10, 0));
        state.setLastAskedField("date");
        state.setMode(SessionState.Mode.BOOKING);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("2020-01-01");
        LlmExtractionResponse ext = new LlmExtractionResponse();
        ext.setIntent("PROVIDE_INFO");
        ext.setDate("2020-01-01");
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(ext);
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudio");

        VoiceResponse response = voiceService.processVoice(mockFile, state.getSessionId());

        assertNull(state.getDate());
        assertNotEquals(SessionState.Mode.CONFIRMATION, state.getMode());
        assertEquals(SessionState.Mode.BOOKING, state.getMode());
    }
}
