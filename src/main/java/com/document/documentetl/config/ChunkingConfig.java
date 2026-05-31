package com.document.documentetl.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChunkingConfig {

    @Bean
    public Tokenizer defaultTokenizer() {
        return new DefaultTokenizer();
    }

    @Bean
    public DocumentSplitter documentSplitter(Tokenizer tokenizer,
                                             @Value("${app.chunk.size:500}") int chunkSize,
                                             @Value("${app.chunk.overlap:100}") int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("app.chunk.size must be greater than 0");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("app.chunk.overlap must be greater than or equal to 0");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("app.chunk.overlap must be smaller than app.chunk.size");
        }
        return DocumentSplitters.recursive(chunkSize, chunkOverlap, tokenizer);
    }

    private static final class DefaultTokenizer implements Tokenizer {

        @Override
        public int estimateTokenCountInText(String text) {
            return countTokens(text);
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return countTokens(message == null ? null : message.toString());
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int total = 0;
            if (messages == null) {
                return total;
            }
            for (ChatMessage message : messages) {
                total += estimateTokenCountInMessage(message);
            }
            return total;
        }

        @Override
        public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
            int total = 0;
            if (toolSpecifications == null) {
                return total;
            }
            for (ToolSpecification toolSpecification : toolSpecifications) {
                total += countTokens(toolSpecification == null ? null : toolSpecification.toString());
            }
            return total;
        }

        @Override
        public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
            int total = 0;
            if (toolExecutionRequests == null) {
                return total;
            }
            for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
                total += countTokens(toolExecutionRequest == null ? null : toolExecutionRequest.toString());
            }
            return total;
        }

        private static int countTokens(String value) {
            if (value == null || value.isBlank()) {
                return 0;
            }
            String[] tokens = value.trim().split("\\s+");
            return tokens.length;
        }
    }
}
