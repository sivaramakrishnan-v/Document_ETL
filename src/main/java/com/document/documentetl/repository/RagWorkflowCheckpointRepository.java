package com.document.documentetl.repository;

import com.document.documentetl.model.RagWorkflowCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RagWorkflowCheckpointRepository extends JpaRepository<RagWorkflowCheckpoint, UUID> {

    Optional<RagWorkflowCheckpoint> findTopByThreadIdOrderByCreatedAtDesc(String threadId);

    List<RagWorkflowCheckpoint> findByThreadIdOrderByCreatedAtDesc(String threadId);
}
