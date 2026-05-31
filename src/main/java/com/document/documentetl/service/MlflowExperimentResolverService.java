package com.document.documentetl.service;

import org.mlflow.tracking.MlflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MlflowExperimentResolverService {

    private static final Logger log = LoggerFactory.getLogger(MlflowExperimentResolverService.class);
    private static final String DEFAULT_EXPERIMENT_ID = "0";

    private final MlflowClient mlflowClient;
    private final String configuredExperimentId;
    private final String configuredExperimentName;

    private volatile String cachedExperimentId;

    public MlflowExperimentResolverService(MlflowClient mlflowClient,
                                           @Value("${app.mlflow.experiment-id:}") String configuredExperimentId,
                                           @Value("${app.mlflow.experiment-name:DocumentETL}") String configuredExperimentName) {
        this.mlflowClient = mlflowClient;
        this.configuredExperimentId = configuredExperimentId;
        this.configuredExperimentName = configuredExperimentName;
    }

    public String resolveExperimentId() {
        String localCache = cachedExperimentId;
        if (localCache != null && !localCache.isBlank()) {
            return localCache;
        }

        synchronized (this) {
            if (cachedExperimentId != null && !cachedExperimentId.isBlank()) {
                return cachedExperimentId;
            }

            String explicitExperimentId = normalizeConfiguredExperimentId(configuredExperimentId);
            if (explicitExperimentId != null) {
                cachedExperimentId = explicitExperimentId;
                log.info("Using explicitly configured MLflow experiment id: {}", cachedExperimentId);
                return cachedExperimentId;
            }

            String normalizedExperimentName = normalizeConfiguredExperimentName(configuredExperimentName);
            if (normalizedExperimentName == null) {
                cachedExperimentId = DEFAULT_EXPERIMENT_ID;
                return cachedExperimentId;
            }

            try {
                var existingExperiment = mlflowClient.getExperimentByName(normalizedExperimentName);
                if (existingExperiment != null
                        && existingExperiment.isPresent()
                        && existingExperiment.get().getExperimentId() != null) {
                    cachedExperimentId = existingExperiment.get().getExperimentId();
                    log.info("Resolved MLflow experiment by name: name={}, id={}", normalizedExperimentName, cachedExperimentId);
                    return cachedExperimentId;
                }

                cachedExperimentId = mlflowClient.createExperiment(normalizedExperimentName);
                log.info("Created MLflow experiment: name={}, id={}", normalizedExperimentName, cachedExperimentId);
                return cachedExperimentId;
            } catch (Exception ex) {
                log.warn("Failed to resolve/create MLflow experiment '{}', falling back to default experiment id {}: {}",
                        normalizedExperimentName,
                        DEFAULT_EXPERIMENT_ID,
                        ex.getMessage());
                cachedExperimentId = DEFAULT_EXPERIMENT_ID;
                return cachedExperimentId;
            }
        }
    }

    private static String normalizeConfiguredExperimentId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.equals(DEFAULT_EXPERIMENT_ID)) {
            return null;
        }
        return trimmed;
    }

    private static String normalizeConfiguredExperimentName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
