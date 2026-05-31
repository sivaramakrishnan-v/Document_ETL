package com.document.documentetl.service.evaluation;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class EvaluationModelGateway {

    private static final Logger log = LoggerFactory.getLogger(EvaluationModelGateway.class);

    private final ChatLanguageModel evaluationChatLanguageModel;

    public EvaluationModelGateway(@Qualifier("evaluationChatLanguageModel") ChatLanguageModel evaluationChatLanguageModel) {
        this.evaluationChatLanguageModel = evaluationChatLanguageModel;
    }

    @CircuitBreaker(name = "evaluationLlm", fallbackMethod = "fallbackGenerate")
    public String generate(String prompt) {
        return evaluationChatLanguageModel.generate(prompt);
    }

    private String fallbackGenerate(String prompt, Throwable throwable) {
        log.warn("Evaluation model unavailable. Circuit breaker fallback applied: {}", throwable.getMessage());
        return "{\"score\":0.0,\"reasoning\":\"Evaluation model unavailable; fallback applied.\"}";
    }
}
