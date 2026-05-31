package com.document.documentetl.config;

import com.document.documentetl.service.MlflowLoggingService;
import com.document.documentetl.service.MlflowExperimentResolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mlflow.startup-test", name = "enabled", havingValue = "true")
public class MlflowStartupVerificationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MlflowStartupVerificationRunner.class);

    private final MlflowLoggingService mlflowLoggingService;
    private final MlflowExperimentResolverService mlflowExperimentResolverService;

    public MlflowStartupVerificationRunner(MlflowLoggingService mlflowLoggingService,
                                           MlflowExperimentResolverService mlflowExperimentResolverService) {
        this.mlflowLoggingService = mlflowLoggingService;
        this.mlflowExperimentResolverService = mlflowExperimentResolverService;
    }

    @Override
    public void run(String... args) {
        String experimentId = mlflowExperimentResolverService.resolveExperimentId();
        String runId = mlflowLoggingService.startNewRun(experimentId);
        if (runId == null || runId.isBlank()) {
            log.warn("MLflow startup verification skipped because run creation failed for experiment {}", experimentId);
            return;
        }

        Map<String, Double> dummyMetrics = Map.of(
                "startup_dummy_metric", 1.0d,
                "startup_epoch_seconds", (double) Instant.now().getEpochSecond()
        );

        mlflowLoggingService.logExperimentMetricsAsync(runId, dummyMetrics);
        log.info("MLflow startup verification submitted. runId={}, experimentId={}, metric=startup_dummy_metric", runId, experimentId);
        log.info("Check your MLflow DB or UI for this runId to confirm persistence.");
    }
}
