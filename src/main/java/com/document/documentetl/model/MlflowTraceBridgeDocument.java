package com.document.documentetl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mlflow_trace_bridge_documents", schema = "knowledge")
public class MlflowTraceBridgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private MlflowTraceBridgeEvent event;

    @Column(name = "doc_id")
    private Long docId;

    @Column(name = "source", columnDefinition = "text")
    private String source;

    @Column(name = "source_path", columnDefinition = "text")
    private String sourcePath;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(name = "similarity")
    private Double similarity;

    @Column(name = "page_content", columnDefinition = "text")
    private String pageContent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public MlflowTraceBridgeDocument() {
        // JPA default constructor
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MlflowTraceBridgeEvent getEvent() {
        return event;
    }

    public void setEvent(MlflowTraceBridgeEvent event) {
        this.event = event;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public int getRankOrder() {
        return rankOrder;
    }

    public void setRankOrder(int rankOrder) {
        this.rankOrder = rankOrder;
    }

    public Double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    public String getPageContent() {
        return pageContent;
    }

    public void setPageContent(String pageContent) {
        this.pageContent = pageContent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
