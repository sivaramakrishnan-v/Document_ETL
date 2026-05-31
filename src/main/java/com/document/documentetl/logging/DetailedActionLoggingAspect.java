package com.document.documentetl.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

@Aspect
@Component
@ConditionalOnProperty(prefix = "app.logging.detailed-actions", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DetailedActionLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(DetailedActionLoggingAspect.class);
    private static final int MAX_STRING = 180;
    private static final int MAX_ITEMS = 8;

    @Around("(" +
            "execution(public * com.document.documentetl.controller..*(..)) || " +
            "execution(public * com.document.documentetl.service..*(..))" +
            ") && !within(com.document.documentetl.logging..*)")
    public Object logAction(ProceedingJoinPoint joinPoint) throws Throwable {
        int step = ActionTraceContext.nextStep();
        String flowId = resolveFlowId();
        String methodName = buildMethodName(joinPoint);
        String argsSummary = summarizeArgs(joinPoint.getArgs());
        long startedAtNanos = System.nanoTime();

        log.info("flow={} step={} action={} state=STARTED args={}",
                flowId,
                step,
                methodName,
                argsSummary);

        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("flow={} step={} action={} state=COMPLETED durationMs={} result={}",
                    flowId,
                    step,
                    methodName,
                    durationMs,
                    summarizeResult(result));
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("flow={} step={} action={} state=FAILED durationMs={} error={}",
                    flowId,
                    step,
                    methodName,
                    durationMs,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    private static String resolveFlowId() {
        String flowId = ActionTraceContext.getFlowId();
        return flowId != null && !flowId.isBlank() ? flowId : "system";
    }

    private static String buildMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private static String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        int rendered = 0;
        for (Object arg : args) {
            if (rendered >= MAX_ITEMS) {
                joiner.add("...(" + (args.length - MAX_ITEMS) + " more)");
                break;
            }
            joiner.add(summarizeValue(arg));
            rendered++;
        }
        return joiner.toString();
    }

    private static String summarizeResult(Object result) {
        return summarizeValue(result);
    }

    private static String summarizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + truncate(text) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof Collection<?> collection) {
            return value.getClass().getSimpleName() + "(size=" + collection.size() + ")";
        }
        if (value instanceof Map<?, ?> map) {
            return value.getClass().getSimpleName() + "(size=" + map.size() + ")";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[](size=" + Array.getLength(value) + ")";
        }
        return value.getClass().getSimpleName();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_STRING) {
            return normalized;
        }
        return normalized.substring(0, MAX_STRING) + "...";
    }
}
