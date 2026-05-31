package com.document.documentetl.logging;

import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicInteger;

public final class ActionTraceContext {

    private static final String MDC_FLOW_ID = "flowId";
    private static final ThreadLocal<AtomicInteger> STEP_COUNTER =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));

    private ActionTraceContext() {
    }

    public static void setFlowId(String flowId) {
        if (flowId == null || flowId.isBlank()) {
            MDC.remove(MDC_FLOW_ID);
            return;
        }
        MDC.put(MDC_FLOW_ID, flowId);
    }

    public static String getFlowId() {
        return MDC.get(MDC_FLOW_ID);
    }

    public static int nextStep() {
        return STEP_COUNTER.get().incrementAndGet();
    }

    public static void resetSteps() {
        STEP_COUNTER.set(new AtomicInteger(0));
    }

    public static void clear() {
        STEP_COUNTER.remove();
        MDC.remove(MDC_FLOW_ID);
    }
}
