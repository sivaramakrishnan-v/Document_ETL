package com.document.documentetl.dto;

public class ChatAskRequest {

    private String question;
    private String goldenAnswer;

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
}
