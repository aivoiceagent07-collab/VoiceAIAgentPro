package com.voiceai.contact.domain.voice.service;

import com.voiceai.contact.domain.voice.client.SarvamClient;
import com.voiceai.contact.domain.voice.util.TtsCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TtsPreCacher implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TtsPreCacher.class);

    private final SarvamClient sarvamClient;
    private final TtsCache ttsCache;

    public TtsPreCacher(SarvamClient sarvamClient, TtsCache ttsCache) {
        this.sarvamClient = sarvamClient;
        this.ttsCache = ttsCache;
    }

    @Override
    public void run(String... args) {
        log.info("Starting pre-caching of common system responses...");
        List<String> commonTexts = List.of(
            "नमस्ते, मैं क्लिनिक की रिसेप्शनिस्ट हूँ। मैं आपकी क्या मदद कर सकती हूँ?",
            "माफ़ कीजिए, मुझे समझ नहीं आया। क्या आप फिर से बता सकते हैं?",
            "मुझे आपकी आवाज़ सुनाई नहीं दी। कृपया फिर से प्रयास करें।",
            "कृपया अपना नाम बताइए।",
            "किस विभाग में दिखाना है?",
            "कृपया बताएं आपको किस तरह के डॉक्टर को दिखाना है, जैसे हड्डी, दिल, नसें या त्वचा।",
            "किस दिन आना चाहेंगे?",
            "माफ़ कीजिए, तारीख़ स्पष्ट नहीं हुई। कोई एक दिन बताएं जैसे सोमवार या मंगलवार।",
            "आपने जो तारीख़ बताई वह स्पष्ट नहीं है। कृपया सिर्फ एक दिन बताएं।",
            "कृपया समय बता दीजिए।",
            "माफ़ कीजिए, समय स्पष्ट नहीं हुआ। कृपया बताएं जैसे सुबह दस बजे या दोपहर बारह बजे।",
            "कृपया सटीक समय बताएं जैसे दोपहर बारह बजे या शाम चार बजे।",
            "ठीक है, कृपया नया समय बताइए।",
            "आपकी अपॉइंटमेंट कन्फर्म हो गई है। क्या आपको और मदद चाहिए?",
            "ठीक है, अपॉइंटमेंट कैंसिल कर दी गई है।",
            "धन्यवाद। आपका दिन शुभ हो।",
            "आपने दो तारीखें बताई हैं। कृपया एक तारीख़ चुनें।",
            "माफ़ कीजिए, कुछ तकनीकी दिक्कत आई।"
        );

        for (String text : commonTexts) {
            try {
                String audio = sarvamClient.synthesizeSpeech(text);
                if (audio != null && !audio.isEmpty()) {
                    ttsCache.put(text, audio);
                }
            } catch (Exception e) {
                log.error("Failed to pre-cache: '{}'. Error: {}", text, e.getMessage());
            }
        }
        log.info("Completed pre-caching.");
    }
}
