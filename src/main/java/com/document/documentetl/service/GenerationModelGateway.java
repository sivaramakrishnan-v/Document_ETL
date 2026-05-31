package com.document.documentetl.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GenerationModelGateway {

    private static final Logger log = LoggerFactory.getLogger(GenerationModelGateway.class);

    private final ChatLanguageModel generationChatLanguageModel;
    private final TokenUsageManagerService tokenUsageManagerService;
    private final String generationModelName;

    public GenerationModelGateway(@Qualifier("generationChatLanguageModel") ChatLanguageModel generationChatLanguageModel,
                                  TokenUsageManagerService tokenUsageManagerService,
                                  @Value("${app.rag.generation.model-name:unknown-model}") String generationModelName) {
        this.generationChatLanguageModel = generationChatLanguageModel;
        this.tokenUsageManagerService = tokenUsageManagerService;
        this.generationModelName = generationModelName;
    }

    @CircuitBreaker(name = "generationLlm", fallbackMethod = "fallbackGenerate")
    public String generate(String prompt) {
        String response = generationChatLanguageModel.generate(prompt);
        tokenUsageManagerService.recordSuccess("generation.default", generationModelName, prompt, response);
        return response;
    }

    @CircuitBreaker(name = "generationLlm", fallbackMethod = "fallbackGenerate")
    public String generate(String prompt, String operationName) {
        String normalizedOperation = normalizeOperationName(operationName);
        String response = generationChatLanguageModel.generate(prompt);
        tokenUsageManagerService.recordSuccess(normalizedOperation, generationModelName, prompt, response);
        return response;
    }

    private String fallbackGenerate(String prompt, Throwable throwable) {
        tokenUsageManagerService.recordFailure("generation.default", generationModelName, prompt, throwable);
        log.warn("Generation model unavailable. Circuit breaker fallback applied: {}", throwable.getMessage());
        throw new IllegalStateException("Generation model is temporarily unavailable. Please retry shortly.", throwable);
    }

    private String fallbackGenerate(String prompt, String operationName, Throwable throwable) {
        tokenUsageManagerService.recordFailure(normalizeOperationName(operationName), generationModelName, prompt, throwable);
        log.warn("Generation model unavailable. Circuit breaker fallback applied: {}", throwable.getMessage());
        throw new IllegalStateException("Generation model is temporarily unavailable. Please retry shortly.", throwable);
    }

    private static String normalizeOperationName(String operationName) {
        if (operationName == null || operationName.isBlank()) {
            return "generation.unknown";
        }
        return operationName.trim();
    }
}
