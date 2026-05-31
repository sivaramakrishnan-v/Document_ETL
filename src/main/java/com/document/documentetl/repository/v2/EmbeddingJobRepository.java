package com.document.documentetl.repository.v2;

import com.document.documentetl.model.v2.EmbeddingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmbeddingJobRepository extends JpaRepository<EmbeddingJob, UUID> {
    Optional<EmbeddingJob> findByChunkIdAndModelProviderAndEmbeddingModelAndEmbeddingDimension(
            UUID chunkId,
            String modelProvider,
            String embeddingModel,
            int embeddingDimension);

    long countByJobStatus(String jobStatus);
}
