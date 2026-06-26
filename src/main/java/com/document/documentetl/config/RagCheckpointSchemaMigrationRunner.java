package com.document.documentetl.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class RagCheckpointSchemaMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagCheckpointSchemaMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public RagCheckpointSchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                ALTER TABLE IF EXISTS document_etl.rag_workflow_checkpoint
                ADD COLUMN IF NOT EXISTS agent_visited JSONB,
                ADD COLUMN IF NOT EXISTS agent_feedback JSONB
                """);
        log.info("Verified RAG checkpoint agent trace columns.");
    }
}
