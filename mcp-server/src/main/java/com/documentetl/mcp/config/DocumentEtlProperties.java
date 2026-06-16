package com.documentetl.mcp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "document-etl")
public record DocumentEtlProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Valid @NotNull Endpoints endpoints
) {
    public record Endpoints(
            @NotBlank String upload,
            @NotBlank String status,
            @NotBlank String ask,
            @NotBlank String search
    ) {
    }
}
