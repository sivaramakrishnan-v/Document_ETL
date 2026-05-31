package com.document.documentetl.dto;

public class EvaluationResultDto {

    private String metric;
    private double score;
    private String reasoning;

    public EvaluationResultDto() {
    }

    public EvaluationResultDto(String metric, double score, String reasoning) {
        this.metric = metric;
        this.score = score;
        this.reasoning = reasoning;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
