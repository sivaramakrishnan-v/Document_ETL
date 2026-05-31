package com.document.documentetl.repository;

import com.document.documentetl.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    boolean existsByDocumentId(Long documentId);

    boolean existsByDocumentIdAndContentHash(Long documentId, String contentHash);

    long deleteByDocumentId(Long documentId);

    Optional<DocumentChunk> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);

    List<DocumentChunk> findByEmbeddingIsNull();
}
