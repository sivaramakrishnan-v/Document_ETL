package com.document.documentetl.dto;

import java.util.List;

public class AgenticAskResponse {

    private String threadId;
    private String answer;
    private String contextGrade;
    private String validationOutcome;
    private int retrievalAttempts;
    private int rewriteAttempts;
    private int answerRevisionAttempts;
    private List<String> citations;
    private List<String> visited;
    private List<String> feedback;

    public AgenticAskResponse() {
    }

    public AgenticAskResponse(String threadId,
                              String answer,
                              String contextGrade,
                              String validationOutcome,
                              int retrievalAttempts,
                              int rewriteAttempts,
                              int answerRevisionAttempts,
                              List<String> citations,
                              List<String> visited,
                              List<String> feedback) {
        this.threadId = threadId;
        this.answer = answer;
        this.contextGrade = contextGrade;
        this.validationOutcome = validationOutcome;
        this.retrievalAttempts = retrievalAttempts;
        this.rewriteAttempts = rewriteAttempts;
        this.answerRevisionAttempts = answerRevisionAttempts;
        this.citations = citations;
        this.visited = visited;
        this.feedback = feedback;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getContextGrade() {
        return contextGrade;
    }

    public void setContextGrade(String contextGrade) {
        this.contextGrade = contextGrade;
    }

    public String getValidationOutcome() {
        return validationOutcome;
    }

    public void setValidationOutcome(String validationOutcome) {
        this.validationOutcome = validationOutcome;
    }

    public int getRetrievalAttempts() {
        return retrievalAttempts;
    }

    public void setRetrievalAttempts(int retrievalAttempts) {
        this.retrievalAttempts = retrievalAttempts;
    }

    public int getRewriteAttempts() {
        return rewriteAttempts;
    }

    public void setRewriteAttempts(int rewriteAttempts) {
        this.rewriteAttempts = rewriteAttempts;
    }

    public int getAnswerRevisionAttempts() {
        return answerRevisionAttempts;
    }

    public void setAnswerRevisionAttempts(int answerRevisionAttempts) {
        this.answerRevisionAttempts = answerRevisionAttempts;
    }

    public List<String> getCitations() {
        return citations;
    }

    public void setCitations(List<String> citations) {
        this.citations = citations;
    }

    public List<String> getVisited() {
        return visited;
    }

    public void setVisited(List<String> visited) {
        this.visited = visited;
    }

    public List<String> getFeedback() {
        return feedback;
    }

    public void setFeedback(List<String> feedback) {
        this.feedback = feedback;
    }
}

