package com.document.documentetl.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.rag")
public class AgentRagProperties {

    private static final Logger log = LoggerFactory.getLogger(AgentRagProperties.class);

    private int maxToolCalls = 4;
    private int maxEvidence = 12;
    private int maxRetrievalAttempts = 2;
    private int perToolLimit = 10;
    private int evidenceSnippetCharLimit = 1200;
    private int mmrCandidateK = 30;
    private int mmrFinalK = 10;
    private double mmrLambda = 0.7;

    @PostConstruct
    void logEffectiveConfig() {
        log.info(
                "Effective RAG config: maxToolCalls={} maxEvidence={} maxRetrievalAttempts={} perToolLimit={} evidenceSnippetCharLimit={} mmrCandidateK={} mmrFinalK={} mmrLambda={}",
                maxToolCalls,
                maxEvidence,
                maxRetrievalAttempts,
                perToolLimit,
                evidenceSnippetCharLimit,
                mmrCandidateK,
                mmrFinalK,
                mmrLambda
        );
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxEvidence() {
        return maxEvidence;
    }

    public void setMaxEvidence(int maxEvidence) {
        this.maxEvidence = maxEvidence;
    }

    public int getMaxRetrievalAttempts() {
        return maxRetrievalAttempts;
    }

    public void setMaxRetrievalAttempts(int maxRetrievalAttempts) {
        this.maxRetrievalAttempts = maxRetrievalAttempts;
    }

    public int getPerToolLimit() {
        return perToolLimit;
    }

    public void setPerToolLimit(int perToolLimit) {
        this.perToolLimit = perToolLimit;
    }

    public int getEvidenceSnippetCharLimit() {
        return evidenceSnippetCharLimit;
    }

    public void setEvidenceSnippetCharLimit(int evidenceSnippetCharLimit) {
        this.evidenceSnippetCharLimit = evidenceSnippetCharLimit;
    }

    public int getMmrCandidateK() {
        return mmrCandidateK;
    }

    public void setMmrCandidateK(int mmrCandidateK) {
        this.mmrCandidateK = mmrCandidateK;
    }

    public int getMmrFinalK() {
        return mmrFinalK;
    }

    public void setMmrFinalK(int mmrFinalK) {
        this.mmrFinalK = mmrFinalK;
    }

    public double getMmrLambda() {
        return mmrLambda;
    }

    public void setMmrLambda(double mmrLambda) {
        this.mmrLambda = mmrLambda;
    }
}
