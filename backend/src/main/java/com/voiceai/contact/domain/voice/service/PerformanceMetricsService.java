package com.voiceai.contact.domain.voice.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

@Service
public class PerformanceMetricsService {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    private final AtomicLong routingDictionary = new AtomicLong(0);
    private final AtomicLong routingSimilarity = new AtomicLong(0);
    private final AtomicLong routingLlm = new AtomicLong(0);

    private final DoubleAdder totalSttTime = new DoubleAdder();
    private final AtomicLong sttCount = new AtomicLong(0);

    private final DoubleAdder totalLlmTime = new DoubleAdder();
    private final AtomicLong llmCount = new AtomicLong(0);

    private final DoubleAdder totalTtsTime = new DoubleAdder();
    private final AtomicLong ttsCount = new AtomicLong(0);

    private final DoubleAdder totalReqTime = new DoubleAdder();

    public void recordRequest(double reqTimeMs) {
        totalRequests.incrementAndGet();
        totalReqTime.add(reqTimeMs);
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordRouting(String source) {
        if ("dictionary".equalsIgnoreCase(source)) {
            routingDictionary.incrementAndGet();
        } else if ("similarity".equalsIgnoreCase(source)) {
            routingSimilarity.incrementAndGet();
        } else {
            routingLlm.incrementAndGet();
        }
    }

    public void recordStt(double timeMs) {
        sttCount.incrementAndGet();
        totalSttTime.add(timeMs);
    }

    public void recordLlm(double timeMs) {
        llmCount.incrementAndGet();
        totalLlmTime.add(timeMs);
    }

    public void recordTts(double timeMs) {
        ttsCount.incrementAndGet();
        totalTtsTime.add(timeMs);
    }

    public String generateReport() {
        long requests = totalRequests.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = (hits + misses) == 0 ? 0.0 : (double) hits / (hits + misses) * 100;

        long rDict = routingDictionary.get();
        long rSim = routingSimilarity.get();
        long rLlm = routingLlm.get();

        double avgStt = sttCount.get() == 0 ? 0.0 : totalSttTime.sum() / sttCount.get();
        double avgLlm = llmCount.get() == 0 ? 0.0 : totalLlmTime.sum() / llmCount.get();
        double avgTts = ttsCount.get() == 0 ? 0.0 : totalTtsTime.sum() / ttsCount.get();
        double avgTotal = requests == 0 ? 0.0 : totalReqTime.sum() / requests;

        return String.format(
            "--- PERFORMANCE METRICS REPORT ---\n" +
            "Total Voice Requests: %d\n" +
            "Average Request Latency: %.2f ms\n" +
            "STT (Speech-to-Text) Avg Latency: %.2f ms (Count: %d)\n" +
            "LLM (Groq) Avg Latency: %.2f ms (Count: %d)\n" +
            "TTS (Text-to-Speech) Avg Latency: %.2f ms (Count: %d)\n" +
            "TTS Cache Hit Rate: %.2f%% (Hits: %d, Misses: %d)\n" +
            "Department Routing Source - Dictionary: %d, Similarity: %d, LLM: %d\n" +
            "----------------------------------",
            requests, avgTotal, avgStt, sttCount.get(), avgLlm, llmCount.get(), avgTts, ttsCount.get(),
            hitRate, hits, misses, rDict, rSim, rLlm
        );
    }
}
