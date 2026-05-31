package com.document.documentetl.repository.v2;

import com.document.documentetl.model.v2.ChunkEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChunkEmbeddingRepository extends JpaRepository<ChunkEmbedding, UUID> {
    boolean existsByChunkIdAndModelProviderAndEmbeddingModelAndEmbeddingDimension(
            UUID chunkId,
            String modelProvider,
            String embeddingModel,
            int embeddingDimension);
}
