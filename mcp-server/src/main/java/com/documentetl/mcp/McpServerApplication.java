package com.documentetl.mcp;

import com.documentetl.mcp.tools.DocumentEtlTools;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.CountDownLatch;

@SpringBootApplication
public class McpServerApplication {

    private static final Logger log = LoggerFactory.getLogger(McpServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    CommandLineRunner mcpServerRunner(McpJsonMapper mcpJsonMapper, DocumentEtlTools documentEtlTools) {
        return args -> {
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(mcpJsonMapper);
            McpSyncServer server = McpServer.sync(transportProvider)
                    .serverInfo("document-etl-mcp-server", "0.0.1")
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .logging()
                            .build())
                    .tools(documentEtlTools.toolSpecifications())
                    .build();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Closing DocumentETL MCP server");
                server.close();
            }, "document-etl-mcp-shutdown"));

            log.info("DocumentETL MCP server started with STDIO transport");
            new CountDownLatch(1).await();
        };
    }
}
