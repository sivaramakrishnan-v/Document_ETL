package com.document.documentetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRagPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsConfiguredRagLimits() {
        contextRunner
                .withPropertyValues(
                        "agent.rag.max-tool-calls=5",
                        "agent.rag.max-evidence=14",
                        "agent.rag.max-retrieval-attempts=3",
                        "agent.rag.per-tool-limit=11",
                        "agent.rag.evidence-snippet-char-limit=1300",
                        "agent.rag.mmr-candidate-k=31",
                        "agent.rag.mmr-final-k=9",
                        "agent.rag.mmr-lambda=0.65"
                )
                .run(context -> {
                    AgentRagProperties properties = context.getBean(AgentRagProperties.class);

                    assertEquals(5, properties.getMaxToolCalls());
                    assertEquals(14, properties.getMaxEvidence());
                    assertEquals(3, properties.getMaxRetrievalAttempts());
                    assertEquals(11, properties.getPerToolLimit());
                    assertEquals(1300, properties.getEvidenceSnippetCharLimit());
                    assertEquals(31, properties.getMmrCandidateK());
                    assertEquals(9, properties.getMmrFinalK());
                    assertEquals(0.65, properties.getMmrLambda());
                });
    }

    @Configuration
    @EnableConfigurationProperties(AgentRagProperties.class)
    static class TestConfig {
    }
}
