package com.document.documentetl.dto;

public class MmrStepDto {

    private int rank;
    private Long documentId;
    private double similarity;
    private double mmrScore;
    private double maxSimilarityToSelected;
    private String snippet;

    public MmrStepDto() {
    }

    public MmrStepDto(int rank,
                      Long documentId,
                      double similarity,
                      double mmrScore,
                      double maxSimilarityToSelected,
                      String snippet) {
        this.rank = rank;
        this.documentId = documentId;
        this.similarity = similarity;
        this.mmrScore = mmrScore;
        this.maxSimilarityToSelected = maxSimilarityToSelected;
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

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public double getMmrScore() {
        return mmrScore;
    }

    public void setMmrScore(double mmrScore) {
        this.mmrScore = mmrScore;
    }

    public double getMaxSimilarityToSelected() {
        return maxSimilarityToSelected;
    }

    public void setMaxSimilarityToSelected(double maxSimilarityToSelected) {
        this.maxSimilarityToSelected = maxSimilarityToSelected;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
