package com.document.documentetl.dto;

import java.util.List;

public class ChatAskResponse {

    private String answer;
    private List<Long> sources;
    private List<EvaluationResultDto> evaluations;

    public ChatAskResponse() {
    }

    public ChatAskResponse(String answer, List<Long> sources, List<EvaluationResultDto> evaluations) {
        this.answer = answer;
        this.sources = sources;
        this.evaluations = evaluations;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Long> getSources() {
        return sources;
    }

    public void setSources(List<Long> sources) {
        this.sources = sources;
    }

    public List<EvaluationResultDto> getEvaluations() {
        return evaluations;
    }

    public void setEvaluations(List<EvaluationResultDto> evaluations) {
        this.evaluations = evaluations;
    }
}
