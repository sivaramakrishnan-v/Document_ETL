package com.document.documentetl.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ChatAskRequest {

    @JsonAlias({"question", "query"})
    private String question;
    private String goldenAnswer;
    @JsonAlias({"threadId", "thread_id"})
    private String threadId;

    public ChatAskRequest() {
    }

    public ChatAskRequest(String question) {
        this.question = question;
    }

    public ChatAskRequest(String question, String goldenAnswer) {
        this.question = question;
        this.goldenAnswer = goldenAnswer;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getGoldenAnswer() {
        return goldenAnswer;
    }

    public void setGoldenAnswer(String goldenAnswer) {
        this.goldenAnswer = goldenAnswer;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }
}
