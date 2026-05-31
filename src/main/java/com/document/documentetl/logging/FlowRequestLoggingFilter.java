package com.document.documentetl.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.logging.detailed-actions", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FlowRequestLoggingFilter.class);
    private static final String FLOW_HEADER = "X-Flow-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String flowId = resolveFlowId(request);
        ActionTraceContext.setFlowId(flowId);
        ActionTraceContext.resetSteps();

        long startedAtNanos = System.nanoTime();
        log.info("flow={} step=0 action=http.request state=STARTED method={} path={} query={} remote={}",
                flowId,
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteAddr());

        try {
            filterChain.doFilter(request, response);
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.info("flow={} step={} action=http.request state=COMPLETED method={} path={} status={} durationMs={}",
                    flowId,
                    ActionTraceContext.nextStep(),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs);
        } catch (Exception ex) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            log.error("flow={} step={} action=http.request state=FAILED method={} path={} durationMs={} error={}",
                    flowId,
                    ActionTraceContext.nextStep(),
                    request.getMethod(),
                    request.getRequestURI(),
                    durationMs,
                    ex.getMessage(),
                    ex);
            throw ex;
        } finally {
            ActionTraceContext.clear();
        }
    }

    private static String resolveFlowId(HttpServletRequest request) {
        String headerFlowId = request.getHeader(FLOW_HEADER);
        if (headerFlowId != null && !headerFlowId.isBlank()) {
            return headerFlowId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
