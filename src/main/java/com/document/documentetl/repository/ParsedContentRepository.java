package com.document.documentetl.repository;

import com.document.documentetl.model.ParsedContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParsedContentRepository extends JpaRepository<ParsedContent, UUID> {
    Optional<ParsedContent> findByDocumentId(Long documentId);

    List<ParsedContent> findByParsingStatus(String parsingStatus);
}
