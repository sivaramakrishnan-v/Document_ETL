package com.document.documentetl.repository.v2;

import com.document.documentetl.model.v2.ExtractedContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExtractedContentRepository extends JpaRepository<ExtractedContent, UUID> {
    Optional<ExtractedContent> findByDocumentIdAndContentHash(Long documentId, String contentHash);
}
