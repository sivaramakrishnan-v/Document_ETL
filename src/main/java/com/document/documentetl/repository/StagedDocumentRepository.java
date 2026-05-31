package com.document.documentetl.repository;

import com.document.documentetl.model.StagedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StagedDocumentRepository extends JpaRepository<StagedDocument, Long> {
    List<StagedDocument> findByStatus(String status);

    Optional<StagedDocument> findByFilePath(String filePath);
}
