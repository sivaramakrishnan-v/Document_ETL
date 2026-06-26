package com.document.documentetl.repository;

import com.document.documentetl.model.TokenUsageEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TokenUsageEventRepository extends JpaRepository<TokenUsageEvent, Long> {

    List<TokenUsageEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long deleteByCreatedAtBefore(OffsetDateTime cutoff);

    @Query(value = """
            SELECT
                COALESCE(SUM(prompt_tokens), 0) AS promptTokens,
                COALESCE(SUM(completion_tokens), 0) AS completionTokens,
                COALESCE(SUM(total_tokens), 0) AS totalTokens,
                COUNT(*) AS requestCount,
                COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successCount,
                COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failureCount,
                MIN(created_at) AS firstSeenAt,
                MAX(created_at) AS lastSeenAt
            FROM document_etl.token_usage_events
            """, nativeQuery = true)
    TokenUsageTotalsProjection fetchTotals();

    @Query(value = """
            SELECT
                operation_name AS operationName,
                COUNT(*) AS requestCount,
                COALESCE(SUM(prompt_tokens), 0) AS promptTokens,
                COALESCE(SUM(completion_tokens), 0) AS completionTokens,
                COALESCE(SUM(total_tokens), 0) AS totalTokens
            FROM document_etl.token_usage_events
            GROUP BY operation_name
            ORDER BY totalTokens DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TokenUsageOperationProjection> fetchTopOperations(@Param("limit") int limit);
}
