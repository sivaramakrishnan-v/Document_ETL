(function (App) {
    "use strict";

    async function requestTokenizerPreview() {
        // TODO: Call a Spring Boot tokenizer endpoint when one is exposed.
        return null;
    }

    function formatNumber(value) {
        return Number(value || 0).toLocaleString();
    }

    function formatDate(value) {
        return value ? new Date(value).toLocaleString() : "--";
    }

    function addSummary(label, value) {
        const cell = document.createElement("div");
        const key = document.createElement("span");
        const result = document.createElement("b");
        cell.className = "summary-cell";
        key.textContent = label;
        result.textContent = value;
        cell.append(key, result);
        App.byId("token-usage-summary").appendChild(cell);
    }

    function renderUsageSummary(summary) {
        const container = App.byId("token-usage-summary");
        container.replaceChildren();
        addSummary("Total tokens", formatNumber(summary.totalTokens));
        addSummary("Prompt tokens", formatNumber(summary.promptTokens));
        addSummary("Completion tokens", formatNumber(summary.completionTokens));
        addSummary("Requests", formatNumber(summary.requestCount));
        addSummary("Successful", formatNumber(summary.successCount));
        addSummary("Failures", `${formatNumber(summary.failureCount)} (${Number(summary.failureRatePct || 0).toFixed(1)}%)`);
        addSummary("First seen", formatDate(summary.firstSeenAt));
        addSummary("Last seen", formatDate(summary.lastSeenAt));
    }

    function renderOperations(operations) {
        const container = App.byId("token-usage-operations");
        container.replaceChildren();
        if (!operations || !operations.length) {
            container.textContent = "No operation usage returned.";
            return;
        }
        operations.forEach(operation => {
            const item = document.createElement("div");
            item.className = "list-item";
            item.textContent = `${operation.operationName || "Unnamed operation"} | ${formatNumber(operation.totalTokens)} tokens | ${formatNumber(operation.requestCount)} requests | prompt ${formatNumber(operation.promptTokens)} | completion ${formatNumber(operation.completionTokens)}`;
            container.appendChild(item);
        });
    }

    function renderRuns(runs) {
        const container = App.byId("token-usage-events");
        container.replaceChildren();
        if (!runs || !runs.length) {
            container.textContent = "No recent token usage runs returned.";
            return;
        }
        runs.forEach(run => {
            const item = document.createElement("details");
            const summary = document.createElement("summary");
            const title = document.createElement("b");
            const status = document.createElement("span");
            const list = document.createElement("dl");
            item.className = "history-item";
            title.textContent = run.legacyUncorrelated ? "Legacy uncorrelated action" : `Run ${run.runId}`;
            status.textContent = `${formatNumber(run.totalTokens)} tokens | ${formatNumber(run.actionCount)} action(s) | ${run.status || "--"} | ${formatDate(run.startedAt)}`;
            summary.append(title, status);
            [
                ["Run ID", run.runId],
                ["Prompt tokens", formatNumber(run.promptTokens)],
                ["Completion tokens", formatNumber(run.completionTokens)],
                ["Total tokens", formatNumber(run.totalTokens)],
                ["Actions", formatNumber(run.actionCount)],
                ["Status", run.status],
                ["Started", formatDate(run.startedAt)],
                ["Completed", formatDate(run.completedAt)]
            ].forEach(([label, value]) => {
                const key = document.createElement("dt");
                const result = document.createElement("dd");
                key.textContent = label;
                result.textContent = value === null || value === undefined || value === "" ? "--" : value;
                list.append(key, result);
            });
            const actionsTitle = document.createElement("h4");
            const actions = document.createElement("div");
            actionsTitle.textContent = "Agent / Tool Actions";
            actions.className = "run-actions";
            (run.actions || []).forEach(action => {
                const actionItem = document.createElement("div");
                actionItem.className = "run-action";
                actionItem.textContent = `${action.operationName || "Unnamed operation"} | ${action.modelName || "unknown model"} | prompt ${formatNumber(action.promptTokens)} | completion ${formatNumber(action.completionTokens)} | total ${formatNumber(action.totalTokens)} | ${action.status || "--"}`;
                if (action.errorMessage) {
                    const error = document.createElement("div");
                    error.className = "run-action-error";
                    error.textContent = `Error: ${action.errorMessage}`;
                    actionItem.appendChild(error);
                }
                actions.appendChild(actionItem);
            });
            item.append(summary, list, actionsTitle, actions);
            container.appendChild(item);
        });
    }

    async function fetchJson(url) {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
        return response.json();
    }

    async function refreshUsage() {
        const button = App.byId("token-usage-refresh");
        button.disabled = true;
        try {
            const [summary, runs] = await Promise.all([
                fetchJson("/api/tokens/summary?topOperations=10"),
                fetchJson("/api/dashboard/token-runs?limit=20")
            ]);
            renderUsageSummary(summary);
            renderOperations(summary.topOperations);
            renderRuns(runs);
            App.byId("token-usage-message").textContent = `Token usage refreshed at ${new Date().toLocaleTimeString()}.`;
        } catch (error) {
            App.byId("token-usage-message").textContent = `Unable to load token usage: ${error.message}`;
        } finally {
            button.disabled = false;
        }
    }

    function estimateTokens(text) {
        return text.trim() ? text.trim().split(/\s+/) : [];
    }

    function localPreview(text, chunkSize, overlap) {
        const tokens = estimateTokens(text);
        const chunks = [];
        const step = Math.max(1, chunkSize - overlap);
        for (let start = 0; start < tokens.length; start += step) {
            chunks.push(tokens.slice(start, start + chunkSize).join(" "));
            if (start + chunkSize >= tokens.length) break;
        }
        return { tokens: tokens.length, chunks, chunkSize, overlap, mode: "Frontend word estimate" };
    }

    function render(preview) {
        const summary = App.byId("tokenizer-summary");
        summary.replaceChildren();
        [["Estimated tokens", preview.tokens], ["Chunks", preview.chunks.length], ["Chunk size", preview.chunkSize], ["Overlap", preview.overlap]]
            .forEach(([label, value]) => {
                const cell = document.createElement("div");
                cell.className = "summary-cell";
                cell.innerHTML = `<span>${label}</span><b>${value}</b>`;
                summary.appendChild(cell);
            });
        const chunks = App.byId("tokenizer-chunks");
        chunks.replaceChildren();
        preview.chunks.forEach((text, index) => {
            const item = document.createElement("div");
            item.className = "chunk-item";
            item.textContent = `Chunk ${index + 1}: ${text}`;
            chunks.appendChild(item);
        });
        if (!preview.chunks.length) chunks.textContent = "Enter text to generate a chunk preview.";
    }

    async function calculate() {
        const text = App.byId("tokenizer-text").value;
        const chunkSize = Math.max(1, Number(App.byId("tokenizer-chunk-size").value) || 180);
        const overlap = Math.max(0, Math.min(chunkSize - 1, Number(App.byId("tokenizer-overlap").value) || 0));
        const backendPreview = await requestTokenizerPreview(text, chunkSize, overlap);
        render(backendPreview || localPreview(text, chunkSize, overlap));
    }

    App.register(() => {
        App.byId("token-usage-refresh").addEventListener("click", refreshUsage);
        App.byId("tokenizer-run").addEventListener("click", calculate);
        refreshUsage();
    });
}(window.DocumentETL));
