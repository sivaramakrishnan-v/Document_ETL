package com.document.documentetl.dto;

public class RerankerStepDto {

    private int rank;
    private Long documentId;
    private double semanticScore;
    private double lexicalOverlapScore;
    private double exactPhraseBoost;
    private double finalScore;
    private String snippet;

    public RerankerStepDto() {
    }

    public RerankerStepDto(int rank,
                           Long documentId,
                           double semanticScore,
                           double lexicalOverlapScore,
                           double exactPhraseBoost,
                           double finalScore,
                           String snippet) {
        this.rank = rank;
        this.documentId = documentId;
        this.semanticScore = semanticScore;
        this.lexicalOverlapScore = lexicalOverlapScore;
        this.exactPhraseBoost = exactPhraseBoost;
        this.finalScore = finalScore;
        this.snippet = snippet;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public double getSemanticScore() {
        return semanticScore;
    }

    public void setSemanticScore(double semanticScore) {
        this.semanticScore = semanticScore;
    }

    public double getLexicalOverlapScore() {
        return lexicalOverlapScore;
    }

    public void setLexicalOverlapScore(double lexicalOverlapScore) {
        this.lexicalOverlapScore = lexicalOverlapScore;
    }

    public double getExactPhraseBoost() {
        return exactPhraseBoost;
    }

    public void setExactPhraseBoost(double exactPhraseBoost) {
        this.exactPhraseBoost = exactPhraseBoost;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
