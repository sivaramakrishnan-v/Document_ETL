package com.document.documentetl.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.mlflow.tracking.MlflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MlflowLoggingService {

    private static final Logger log = LoggerFactory.getLogger(MlflowLoggingService.class);

    private final MlflowClient mlflowClient;

    public MlflowLoggingService(MlflowClient mlflowClient) {
        this.mlflowClient = mlflowClient;
    }

    @CircuitBreaker(name = "mlflowWrite", fallbackMethod = "startNewRunFallback")
    public String startNewRun(String experimentId) {
        var runInfo = mlflowClient.createRun(experimentId);
        return runInfo.getRunId();
    }

    public void logExperimentMetrics(String runId, Map<String, Double> metrics) {
        if (runId == null || runId.isBlank() || metrics == null || metrics.isEmpty()) {
            return;
        }

        int loggedMetricsCount = 0;
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String metricName = entry.getKey();
            Double metricValue = entry.getValue();
            if (metricName == null || metricName.isBlank() || metricValue == null) {
                continue;
            }
            mlflowClient.logMetric(runId, metricName, metricValue, System.currentTimeMillis(), 0L);
            loggedMetricsCount++;
        }
        if (loggedMetricsCount > 0) {
            log.info("Logged {} metric(s) to MLflow run {}", loggedMetricsCount, runId);
        }
    }

    @Async("mlflowTaskExecutor")
    @CircuitBreaker(name = "mlflowWrite", fallbackMethod = "logExperimentMetricsAsyncFallback")
    public void logExperimentMetricsAsync(String runId, Map<String, Double> metrics) {
        logExperimentMetrics(runId, metrics);
    }

    @Async("mlflowTaskExecutor")
    @CircuitBreaker(name = "mlflowWrite", fallbackMethod = "startRunAndLogMetricsAsyncFallback")
    public void startRunAndLogMetricsAsync(String experimentId, Map<String, Double> metrics) {
        String runId = startNewRun(experimentId);
        logExperimentMetrics(runId, metrics);
    }

    @Async("mlflowTaskExecutor")
    @CircuitBreaker(name = "mlflowWrite", fallbackMethod = "logActionRunAsyncFallback")
    public void logActionRunAsync(String experimentId,
                                  String actionName,
                                  Map<String, String> params,
                                  Map<String, Double> metrics,
                                  boolean success,
                                  String errorMessage) {
        logActionRun(experimentId, actionName, params, metrics, success, errorMessage);
    }

    @CircuitBreaker(name = "mlflowWrite", fallbackMethod = "logActionRunFallback")
    public String logActionRun(String experimentId,
                               String actionName,
                               Map<String, String> params,
                               Map<String, Double> metrics,
                               boolean success,
                               String errorMessage) {
        if (experimentId == null || experimentId.isBlank() || actionName == null || actionName.isBlank()) {
            return null;
        }

        String runId = startNewRun(experimentId);
        if (runId == null || runId.isBlank()) {
            return null;
        }

        setTagSafely(runId, "action_name", actionName);
        setTagSafely(runId, "action_status", success ? "SUCCESS" : "FAILED");
        if (errorMessage != null && !errorMessage.isBlank()) {
            setTagSafely(runId, "error_message", truncate(errorMessage, 200));
        }

        logParamsSafely(runId, params);

        Map<String, Double> normalizedMetrics = new LinkedHashMap<>();
        if (metrics != null && !metrics.isEmpty()) {
            normalizedMetrics.putAll(metrics);
        }
        normalizedMetrics.putIfAbsent("action_success", success ? 1.0 : 0.0);
        logExperimentMetrics(runId, normalizedMetrics);

        terminateRunSafely(runId);
        log.info("MLflow action run logged: action={}, status={}, runId={}", actionName, success ? "SUCCESS" : "FAILED", runId);
        return runId;
    }

    private String logActionRunFallback(String experimentId,
                                        String actionName,
                                        Map<String, String> params,
                                        Map<String, Double> metrics,
                                        boolean success,
                                        String errorMessage,
                                        Throwable throwable) {
        log.warn("MLflow action run skipped. Circuit breaker fallback applied for action={} experimentId={}: {}",
                actionName,
                experimentId,
                throwable.getMessage());
        return null;
    }

    private String startNewRunFallback(String experimentId, Throwable throwable) {
        log.warn("MLflow run creation skipped. Circuit breaker fallback applied for experiment {}: {}",
                experimentId,
                throwable.getMessage());
        return null;
    }

    private void logExperimentMetricsAsyncFallback(String runId, Map<String, Double> metrics, Throwable throwable) {
        log.warn("MLflow metric logging skipped. Circuit breaker fallback applied for run {}: {}",
                runId,
                throwable.getMessage());
    }

    private void startRunAndLogMetricsAsyncFallback(String experimentId, Map<String, Double> metrics, Throwable throwable) {
        log.warn("MLflow start-and-log skipped. Circuit breaker fallback applied for experiment {}: {}",
                experimentId,
                throwable.getMessage());
    }

    private void logActionRunAsyncFallback(String experimentId,
                                           String actionName,
                                           Map<String, String> params,
                                           Map<String, Double> metrics,
                                           boolean success,
                                           String errorMessage,
                                           Throwable throwable) {
        logActionRunFallback(experimentId, actionName, params, metrics, success, errorMessage, throwable);
    }

    private void logParamsSafely(String runId, Map<String, String> params) {
        if (runId == null || runId.isBlank() || params == null || params.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            try {
                mlflowClient.logParam(runId, key, value);
            } catch (Exception e) {
                log.debug("MLflow param logging skipped for run={}, key={}, reason={}", runId, key, e.getMessage());
            }
        }
    }

    private void setTagSafely(String runId, String key, String value) {
        if (runId == null || runId.isBlank() || key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }

        try {
            mlflowClient.setTag(runId, key, value);
        } catch (Exception e) {
            log.debug("MLflow tag logging skipped for run={}, key={}, reason={}", runId, key, e.getMessage());
        }
    }

    private void terminateRunSafely(String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }

        try {
            mlflowClient.setTerminated(runId);
        } catch (Exception e) {
            log.debug("MLflow run termination skipped for run={}, reason={}", runId, e.getMessage());
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
