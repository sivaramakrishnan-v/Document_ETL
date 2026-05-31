package com.document.documentetl.dto;

public class RetrievalStepItemDto {

    private int rank;
    private Long documentId;
    private double similarity;
    private String snippet;

    public RetrievalStepItemDto() {
    }

    public RetrievalStepItemDto(int rank, Long documentId, double similarity, String snippet) {
        this.rank = rank;
        this.documentId = documentId;
        this.similarity = similarity;
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

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
