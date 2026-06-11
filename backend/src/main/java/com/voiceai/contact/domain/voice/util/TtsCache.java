package com.voiceai.contact.domain.voice.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TtsCache {
    private final int maxEntries;
    private final long ttlMs;
    private final Map<String, CacheEntry> cache;

    public TtsCache(
            @Value("${tts.cache.max-entries:200}") int maxEntries,
            @Value("${tts.cache.ttl-ms:7200000}") long ttlMs) {
        this.maxEntries = maxEntries;
        this.ttlMs = ttlMs;
        this.cache = new LinkedHashMap<>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > TtsCache.this.maxEntries;
            }
        };
    }

    public static class CacheEntry {
        private final String audioBase64;
        private final Instant expiryTime;

        public CacheEntry(String audioBase64, long ttlMs) {
            this.audioBase64 = audioBase64;
            this.expiryTime = Instant.now().plusMillis(ttlMs);
        }

        public String getAudioBase64() {
            return audioBase64;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }

    public synchronized String get(String text) {
        CacheEntry entry = cache.get(text);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(text);
            return null;
        }
        return entry.getAudioBase64();
    }

    public synchronized void put(String text, String audioBase64) {
        cache.put(text, new CacheEntry(audioBase64, ttlMs));
    }

    public synchronized void clear() {
        cache.clear();
    }
}
