package com.document.documentetl.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GenerationModelGateway {

    private static final Logger log = LoggerFactory.getLogger(GenerationModelGateway.class);

    private final ChatLanguageModel generationChatLanguageModel;

    public GenerationModelGateway(@Qualifier("generationChatLanguageModel") ChatLanguageModel generationChatLanguageModel) {
        this.generationChatLanguageModel = generationChatLanguageModel;
    }

    @CircuitBreaker(name = "generationLlm", fallbackMethod = "fallbackGenerate")
    public String generate(String prompt) {
        return generationChatLanguageModel.generate(prompt);
    }

    private String fallbackGenerate(String prompt, Throwable throwable) {
        log.warn("Generation model unavailable. Circuit breaker fallback applied: {}", throwable.getMessage());
        throw new IllegalStateException("Generation model is temporarily unavailable. Please retry shortly.", throwable);
    }
}
