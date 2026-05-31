package com.document.documentetl.dto;

public class SearchResult {

    private final String text;
    private final Long documentId;
    private final double similarity;
    private final float[] embedding;

    public SearchResult(String text, Long documentId, double similarity) {
        this(text, documentId, similarity, null);
    }

    public SearchResult(String text, Long documentId, double similarity, float[] embedding) {
        this.text = text;
        this.documentId = documentId;
        this.similarity = similarity;
        this.embedding = embedding;
    }

    public String getText() {
        return text;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public double getSimilarity() {
        return similarity;
    }

    public float[] getEmbedding() {
        return embedding;
    }
}
