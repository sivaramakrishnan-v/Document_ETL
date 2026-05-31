package com.document.documentetl.dto;

import java.util.List;

public class ChatReviewResponse {

    private String answer;
    private List<Long> sources;
    private List<EvaluationResultDto> evaluations;
    private int topK;
    private int candidateK;
    private double mmrLambda;
    private List<RetrievalStepItemDto> vectorTopK;
    private List<MmrStepDto> mmrOutput;
    private List<RerankerStepDto> rerankerOutput;

    public ChatReviewResponse() {
    }

    public ChatReviewResponse(String answer,
                              List<Long> sources,
                              List<EvaluationResultDto> evaluations,
                              int topK,
                              int candidateK,
                              double mmrLambda,
                              List<RetrievalStepItemDto> vectorTopK,
                              List<MmrStepDto> mmrOutput,
                              List<RerankerStepDto> rerankerOutput) {
        this.answer = answer;
        this.sources = sources;
        this.evaluations = evaluations;
        this.topK = topK;
        this.candidateK = candidateK;
        this.mmrLambda = mmrLambda;
        this.vectorTopK = vectorTopK;
        this.mmrOutput = mmrOutput;
        this.rerankerOutput = rerankerOutput;
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

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getCandidateK() {
        return candidateK;
    }

    public void setCandidateK(int candidateK) {
        this.candidateK = candidateK;
    }

    public double getMmrLambda() {
        return mmrLambda;
    }

    public void setMmrLambda(double mmrLambda) {
        this.mmrLambda = mmrLambda;
    }

    public List<RetrievalStepItemDto> getVectorTopK() {
        return vectorTopK;
    }

    public void setVectorTopK(List<RetrievalStepItemDto> vectorTopK) {
        this.vectorTopK = vectorTopK;
    }

    public List<MmrStepDto> getMmrOutput() {
        return mmrOutput;
    }

    public void setMmrOutput(List<MmrStepDto> mmrOutput) {
        this.mmrOutput = mmrOutput;
    }

    public List<RerankerStepDto> getRerankerOutput() {
        return rerankerOutput;
    }

    public void setRerankerOutput(List<RerankerStepDto> rerankerOutput) {
        this.rerankerOutput = rerankerOutput;
    }
}
