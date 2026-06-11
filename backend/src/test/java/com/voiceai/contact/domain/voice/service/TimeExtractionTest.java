package com.voiceai.contact.domain.voice.service;

import com.voiceai.contact.domain.voice.client.GroqClient;
import com.voiceai.contact.domain.voice.client.SarvamClient;
import com.voiceai.contact.domain.voice.dto.LlmExtractionResponse;
import com.voiceai.contact.domain.voice.dto.VoiceResponse;
import com.voiceai.contact.domain.voice.model.SessionState;
import com.voiceai.contact.domain.voice.util.SpeechFormatter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {
    "SARVAM_API_KEY=dummy-sarvam-key-12345",
    "GROQ_API_KEY=dummy-groq-key-67890"
})
public class TimeExtractionTest {

    @Autowired
    private SpeechFormatter speechFormatter;

    @Autowired
    private VoiceService voiceService;

    @Autowired
    private SessionManagerService sessionManagerService;

    @MockBean
    private SarvamClient sarvamClient;

    @MockBean
    private GroqClient groqClient;

    @Test
    public void testParseTimeToLocalTime() {
        assertEquals(LocalTime.of(15, 0), speechFormatter.parseTimeToLocalTime("3 PM"));
        assertEquals(LocalTime.of(13, 0), speechFormatter.parseTimeToLocalTime("1 PM"));
        assertEquals(LocalTime.of(10, 0), speechFormatter.parseTimeToLocalTime("10:00"));
        assertEquals(LocalTime.of(10, 0), speechFormatter.parseTimeToLocalTime("सुबह 10 बजे"));
        assertEquals(LocalTime.of(12, 0), speechFormatter.parseTimeToLocalTime("दोपहर बारह बजे"));
        assertEquals(LocalTime.of(12, 0), speechFormatter.parseTimeToLocalTime("दोपहर १२ बजे"));
        assertEquals(LocalTime.of(16, 0), speechFormatter.parseTimeToLocalTime("शाम 4 बजे"));
        assertEquals(LocalTime.of(20, 0), speechFormatter.parseTimeToLocalTime("रात 8 बजे"));
    }

    @Test
    public void testBookingFlowTimeAdvance() throws Exception {
        // Create/retrieve state and initialize booking info
        String sessionId = "test-session-time-advance";
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        state.setPatientName("Rahul");
        state.setDepartment("Orthopedic");
        state.setDate("2026-06-15"); // Monday (Dr. Sharma works 10:00 - 14:00)
        state.setLastAskedField("time");
        state.setMode(SessionState.Mode.BOOKING);

        // Stub transcription
        Mockito.when(sarvamClient.transcribeAudio(any())).thenReturn("दोपहर एक बजे");

        // Stub LLM extraction response
        LlmExtractionResponse extracted = new LlmExtractionResponse();
        extracted.setIntent("PROVIDE_INFO");
        extracted.setTime("1 PM");
        Mockito.when(groqClient.extractGroqEntities(anyString(), anyString(), any(SessionState.class))).thenReturn(extracted);

        // Stub TTS
        Mockito.when(sarvamClient.synthesizeSpeech(anyString())).thenReturn("dummyAudioBase64");

        // Invoke processVoice
        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.isEmpty()).thenReturn(false);

        VoiceResponse response = voiceService.processVoice(mockFile, state.getSessionId());

        // Verify time was correctly parsed, validated, and saved in canonical format
        assertNotNull(state.getTime());
        assertEquals(LocalTime.of(13, 0), state.getTime());

        // Verify state machine has advanced to CONFIRMATION mode
        assertEquals(SessionState.Mode.CONFIRMATION, state.getMode());
        assertEquals("confirmation", state.getLastAskedField());
        
        // Verify response contains appropriate confirmation details and base64 audio
        assertNotNull(response.getText());
        assertTrue(response.getText().contains("कन्फर्म"));
        assertEquals("dummyAudioBase64", response.getAudio());
    }
}
