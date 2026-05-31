package com.document.documentetl.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MlflowActionTrackingService {

    private final MlflowLoggingService mlflowLoggingService;
    private final MlflowExperimentResolverService mlflowExperimentResolverService;
    private final boolean enabled;

    public MlflowActionTrackingService(MlflowLoggingService mlflowLoggingService,
                                       MlflowExperimentResolverService mlflowExperimentResolverService,
                                       @Value("${app.mlflow.action-logging.enabled:true}") boolean enabled) {
        this.mlflowLoggingService = mlflowLoggingService;
        this.mlflowExperimentResolverService = mlflowExperimentResolverService;
        this.enabled = enabled;
    }

    public void logActionSuccess(String actionName,
                                 long durationMs,
                                 Map<String, Double> actionMetrics,
                                 Map<String, String> params) {
        logActionSuccessAndGetRunId(actionName, durationMs, actionMetrics, params);
    }

    public String logActionSuccessAndGetRunId(String actionName,
                                              long durationMs,
                                              Map<String, Double> actionMetrics,
                                              Map<String, String> params) {
        if (!enabled) {
            return null;
        }
        Map<String, Double> metrics = buildBaseMetrics(durationMs, true);
        if (actionMetrics != null && !actionMetrics.isEmpty()) {
            metrics.putAll(actionMetrics);
        }
        String experimentId = mlflowExperimentResolverService.resolveExperimentId();
        return mlflowLoggingService.logActionRun(experimentId, actionName, params, metrics, true, null);
    }

    public void logActionFailure(String actionName,
                                 long durationMs,
                                 Exception exception,
                                 Map<String, String> params) {
        if (!enabled) {
            return;
        }
        Map<String, Double> metrics = buildBaseMetrics(durationMs, false);
        String errorMessage = exception != null ? exception.getMessage() : "unknown error";
        String experimentId = mlflowExperimentResolverService.resolveExperimentId();
        mlflowLoggingService.logActionRunAsync(experimentId, actionName, params, metrics, false, errorMessage);
    }

    private static Map<String, Double> buildBaseMetrics(long durationMs, boolean success) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("duration_ms", Math.max(0L, durationMs) * 1.0d);
        metrics.put("action_success", success ? 1.0d : 0.0d);
        return metrics;
    }
}
