package com.document.documentetl.repository.v2;

import com.document.documentetl.model.v2.PipelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineEventRepository extends JpaRepository<PipelineEvent, UUID> {
    long countByProcessingStatus(String processingStatus);

    List<PipelineEvent> findTop10ByOrderByProcessedAtDesc();
}
