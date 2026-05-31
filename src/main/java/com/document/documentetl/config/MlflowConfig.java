package com.document.documentetl.config;

import org.mlflow.tracking.MlflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MlflowConfig {

    private static final Logger log = LoggerFactory.getLogger(MlflowConfig.class);

    @Bean
    public MlflowClient mlflowClient(@Value("${mlflow.tracking-uri}") String trackingUri) {
        String normalizedTrackingUri = normalizeTrackingUri(trackingUri);
        log.info("Configuring MLflow client with tracking URI: {}", normalizedTrackingUri);
        return new MlflowClient(normalizedTrackingUri);
    }

    private String normalizeTrackingUri(String trackingUri) {
        if (trackingUri == null || trackingUri.isBlank()) {
            throw new IllegalArgumentException("mlflow.tracking-uri must be configured");
        }
        if (trackingUri.startsWith("http://") || trackingUri.startsWith("https://")) {
            return trackingUri;
        }
        return "http://" + trackingUri;
    }
}
