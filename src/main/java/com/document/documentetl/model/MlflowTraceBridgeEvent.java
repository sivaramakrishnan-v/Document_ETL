package com.document.documentetl.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mlflow_trace_bridge_events", schema = "knowledge")
public class MlflowTraceBridgeEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_time_epoch_ms", nullable = false)
    private long eventTimeEpochMs;

    @Column(name = "run_id", nullable = false, length = 128)
    private String runId;

    @Column(name = "question", columnDefinition = "text")
    private String question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "root_span_name", length = 128)
    private String rootSpanName;

    @Column(name = "retriever_span_name", length = 128)
    private String retrieverSpanName;

    @Column(name = "retriever_span_type", length = 32)
    private String retrieverSpanType;

    @Column(name = "ingested", nullable = false)
    private boolean ingested = false;

    @Column(name = "ingested_at")
    private OffsetDateTime ingestedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MlflowTraceBridgeDocument> documents = new ArrayList<>();

    public MlflowTraceBridgeEvent() {
        // JPA default constructor
    }

    public void addDocument(MlflowTraceBridgeDocument document) {
        if (document == null) {
            return;
        }
        document.setEvent(this);
        this.documents.add(document);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public long getEventTimeEpochMs() {
        return eventTimeEpochMs;
    }

    public void setEventTimeEpochMs(long eventTimeEpochMs) {
        this.eventTimeEpochMs = eventTimeEpochMs;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getRootSpanName() {
        return rootSpanName;
    }

    public void setRootSpanName(String rootSpanName) {
        this.rootSpanName = rootSpanName;
    }

    public String getRetrieverSpanName() {
        return retrieverSpanName;
    }

    public void setRetrieverSpanName(String retrieverSpanName) {
        this.retrieverSpanName = retrieverSpanName;
    }

    public String getRetrieverSpanType() {
        return retrieverSpanType;
    }

    public void setRetrieverSpanType(String retrieverSpanType) {
        this.retrieverSpanType = retrieverSpanType;
    }

    public boolean isIngested() {
        return ingested;
    }

    public void setIngested(boolean ingested) {
        this.ingested = ingested;
    }

    public OffsetDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(OffsetDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<MlflowTraceBridgeDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<MlflowTraceBridgeDocument> documents) {
        this.documents = documents;
    }
}
