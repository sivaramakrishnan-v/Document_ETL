package com.document.documentetl.repository.v2;

import com.document.documentetl.model.v2.SourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {
    Optional<SourceDocument> findBySourceUri(String sourceUri);

    Optional<SourceDocument> findBySourceUriAndContentHash(String sourceUri, String contentHash);

    long countByStatus(String status);
}
