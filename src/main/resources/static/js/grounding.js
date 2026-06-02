(function (App) {
    "use strict";

    function addSummary(label, value) {
        const cell = document.createElement("div");
        cell.className = "summary-cell";
        const key = document.createElement("span");
        const result = document.createElement("b");
        key.textContent = label;
        result.textContent = value ?? "--";
        cell.append(key, result);
        App.byId("grounding-metrics").appendChild(cell);
    }

    function renderItems(id, items, emptyText) {
        const container = App.byId(id);
        container.replaceChildren();
        if (!items || !items.length) {
            container.textContent = emptyText;
            return;
        }
        items.forEach((text, index) => {
            const item = document.createElement("div");
            item.className = "list-item";
            item.textContent = `${index + 1}. ${text}`;
            container.appendChild(item);
        });
    }

    function explanation(source) {
        const status = source.groundingStatus || "not reported";
        const coverage = App.formatScore(source.citationCoverageScore);
        const unsupported = source.unsupportedClaimsCount ?? "not reported";
        return `Grounding status is ${status}. Citation coverage is ${coverage}, with ${unsupported} unsupported claims reported by the workflow. Select a checkpoint from history to review its persisted evidence.`;
    }

    function detailRow(list, label, value) {
        const key = document.createElement("dt");
        const result = document.createElement("dd");
        key.textContent = label;
        result.textContent = value === null || value === undefined || value === "" ? "--" : value;
        list.append(key, result);
    }

    function renderHistory() {
        const history = App.state.checkpointHistory.length
            ? App.state.checkpointHistory
            : App.state.globalCheckpointHistory || [];
        const container = App.byId("grounding-history");
        container.replaceChildren();
        if (!history.length) {
            container.textContent = "No checkpoint history returned for this thread.";
            return;
        }
        history.forEach(checkpoint => {
            const details = document.createElement("details");
            const summary = document.createElement("summary");
            const title = document.createElement("strong");
            const status = document.createElement("span");
            const list = document.createElement("dl");
            const citations = checkpoint.citations || [];
            const review = document.createElement("button");
            details.className = "history-item";
            review.className = "history-review secondary-button";
            review.type = "button";
            review.textContent = "Review checkpoint";
            review.addEventListener("click", () => App.selectCheckpoint(checkpoint));
            summary.addEventListener("click", () => App.selectCheckpoint(checkpoint));
            title.textContent = checkpoint.userQuery || checkpoint.checkpointId || "Checkpoint";
            status.textContent = `${checkpoint.workflowStatus || "--"} | ${checkpoint.groundingStatus || "not scored"}`;
            summary.append(title, status);
            detailRow(list, "Checkpoint", checkpoint.checkpointId);
            detailRow(list, "Created", checkpoint.createdAt ? new Date(checkpoint.createdAt).toLocaleString() : "--");
            detailRow(list, "Groundedness", App.formatScore(checkpoint.groundednessScore));
            detailRow(list, "Citation coverage", App.formatScore(checkpoint.citationCoverageScore));
            detailRow(list, "Unsupported claims", checkpoint.unsupportedClaimsCount);
            detailRow(list, "Citations", citations.length ? citations.join(", ") : "No citations returned");
            detailRow(list, "Validation", checkpoint.validationStatus);
            detailRow(list, "Retrieval strategy", checkpoint.retrievalStrategy);
            detailRow(list, "Retrieved documents", (checkpoint.retrievedDocumentIds || []).join(", ") || "None");
            detailRow(list, "Retrieved chunks", (checkpoint.retrievedChunkIds || []).join(", ") || "None");
            detailRow(list, "Answer", checkpoint.generatedAnswer);
            detailRow(list, "Error", checkpoint.errorMessage);
            details.append(summary, review, list);
            container.appendChild(details);
        });
    }

    async function loadHistory() {
        const threadId = App.byId("grounding-thread-id").value.trim();
        if (!threadId) {
            App.byId("grounding-history-message").textContent = "Enter a thread ID before loading history.";
            return;
        }
        const button = App.byId("grounding-load-history");
        button.disabled = true;
        try {
            const response = await fetch(`/api/rag/checkpoints/${encodeURIComponent(threadId)}/history`);
            if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
            App.state.checkpointHistory = await response.json();
            App.byId("grounding-history-message").textContent = `Loaded ${App.state.checkpointHistory.length} checkpoint record(s) for ${threadId}.`;
            renderHistory();
        } catch (error) {
            App.byId("grounding-history-message").textContent = `Unable to load checkpoint history: ${error.message}`;
        } finally {
            button.disabled = false;
        }
    }

    function render() {
        const response = App.state.latestAgentResponse;
        const checkpoint = App.state.selectedCheckpoint || App.state.latestCheckpoint;
        const source = checkpoint || response;
        const metrics = App.byId("grounding-metrics");
        metrics.replaceChildren();
        if (!source) return;
        App.byId("grounding-thread-id").value = source.threadId || "";

        const citations = source.citations || [];
        const chunks = checkpoint && (checkpoint.retrievedContextSnapshot || checkpoint.retrievedChunkIds) || [];
        addSummary("Groundedness", App.formatScore(source.groundednessScore));
        addSummary("Citation coverage", App.formatScore(source.citationCoverageScore));
        addSummary("Citations", citations.length);
        addSummary("Matched citations", citations.length);
        addSummary("Retrieved chunks", chunks.length);
        addSummary("Unsupported claims", source.unsupportedClaimsCount);
        App.setBadge("grounding-overall", source.groundingStatus || "Loaded");
        renderItems("grounding-citations", citations, "No citations returned.");
        renderItems("grounding-chunks", chunks, "No checkpoint chunks available.");
        App.setText("grounding-explanation", explanation(source));
    }

    App.register(() => {
        document.addEventListener("agent-response", render);
        document.addEventListener("checkpoint-response", render);
        document.addEventListener("checkpoint-selection", render);
        document.addEventListener("checkpoint-history-response", () => {
            const threadId = App.state.latestAgentResponse && App.state.latestAgentResponse.threadId;
            App.byId("grounding-history-message").textContent = `Loaded ${App.state.checkpointHistory.length} checkpoint record(s) for ${threadId || "the active thread"}.`;
            renderHistory();
        });
        document.addEventListener("global-checkpoint-history-response", () => {
            App.byId("grounding-history-message").textContent = `Loaded ${App.state.globalCheckpointHistory.length} persisted checkpoint record(s) from the database. Enter a thread ID to filter.`;
            renderHistory();
        });
        App.byId("grounding-load-history").addEventListener("click", loadHistory);
    });
}(window.DocumentETL));
