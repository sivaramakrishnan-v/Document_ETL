package com.document.documentetl.repository.v2;

import com.document.documentetl.model.v2.TextChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TextChunkRepository extends JpaRepository<TextChunk, UUID> {
    boolean existsByContentId(UUID contentId);

    List<TextChunk> findByContentIdOrderByChunkIndex(UUID contentId);

    @Query("""
            select chunk
            from TextChunk chunk
            where not exists (
                select embedding
                from ChunkEmbedding embedding
                where embedding.chunkId = chunk.chunkId
                  and embedding.modelProvider = :modelProvider
                  and embedding.embeddingModel = :embeddingModel
                  and embedding.embeddingDimension = :embeddingDimension
            )
            order by chunk.createdAt asc
            """)
    List<TextChunk> findChunksMissingEmbedding(@Param("modelProvider") String modelProvider,
                                               @Param("embeddingModel") String embeddingModel,
                                               @Param("embeddingDimension") int embeddingDimension,
                                               Pageable pageable);

    @Query("""
            select count(chunk)
            from TextChunk chunk
            where not exists (
                select embedding
                from ChunkEmbedding embedding
                where embedding.chunkId = chunk.chunkId
                  and embedding.modelProvider = :modelProvider
                  and embedding.embeddingModel = :embeddingModel
                  and embedding.embeddingDimension = :embeddingDimension
            )
            """)
    long countChunksMissingEmbedding(@Param("modelProvider") String modelProvider,
                                     @Param("embeddingModel") String embeddingModel,
                                     @Param("embeddingDimension") int embeddingDimension);
}
