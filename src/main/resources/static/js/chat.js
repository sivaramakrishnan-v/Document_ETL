(function (App) {
    "use strict";

    function stripCitationMarkers(text) {
        return String(text || "")
            .replace(/\s*\[doc=[^\]]+\]/gi, "")
            .replace(/\s+([,.!?;:])/g, "$1")
            .replace(/\s{2,}/g, " ")
            .trim();
    }

    function appendMessage(role, text, responseTimeMs) {
        const messages = App.byId("chat-messages");
        const empty = messages.querySelector(".empty-state");
        if (empty) empty.remove();
        const bubble = document.createElement("div");
        bubble.className = `message ${role}`;
        bubble.textContent = text || "";
        if (role === "assistant" && responseTimeMs !== undefined) {
            const meta = document.createElement("div");
            meta.className = "message-meta";
            meta.textContent = `Completed in ${responseTimeMs} ms`;
            bubble.appendChild(meta);
        }
        messages.appendChild(bubble);
        messages.scrollTop = messages.scrollHeight;
    }

    async function readError(response) {
        const text = await response.text();
        try {
            const body = JSON.parse(text);
            return body.message || body.error || text;
        } catch (_) {
            return text || `${response.status} ${response.statusText}`;
        }
    }

    async function askAgent(question, threadId) {
        const response = await fetch("/api/chat/agent/ask", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ question, threadId: threadId || null })
        });
        if (!response.ok) throw new Error(await readError(response));
        return response.json();
    }

    async function loadCheckpoint(threadId) {
        if (!threadId) return null;
        const response = await fetch(`/api/rag/checkpoints/${encodeURIComponent(threadId)}/latest`);
        if (!response.ok) return null;
        return response.json();
    }

    async function loadCheckpointHistory(threadId) {
        if (!threadId) return [];
        const response = await fetch(`/api/rag/checkpoints/${encodeURIComponent(threadId)}/history`);
        if (!response.ok) return [];
        return response.json();
    }

    async function fetchCheckpointHistory(threadId) {
        if (!threadId) return [];
        const response = await fetch(`/api/rag/checkpoints/${encodeURIComponent(threadId)}/history`);
        if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
        return response.json();
    }

    function renderSummary(data, elapsed) {
        App.setText("current-thread", data.threadId ? `Thread: ${data.threadId}` : "No active thread");
        App.setText("last-updated", new Date().toLocaleTimeString());
        App.byId("chat-thread-id").value = data.threadId || "";
        App.setText("chat-response-time", `${elapsed} ms`);
        App.setText("chat-workflow-status", data.workflowStatus);
        App.setText("chat-checkpoint", data.checkpointId);
        App.setBadge("chat-status", data.workflowStatus || "Completed");
    }

    async function submit(event) {
        event.preventDefault();
        const question = App.byId("chat-question").value.trim();
        const threadId = App.byId("chat-thread-id").value.trim();
        if (!question) return;

        const button = App.byId("chat-submit");
        button.disabled = true;
        App.setBadge("chat-status", "In progress");
        appendMessage("user", question);
        App.byId("chat-question").value = "";
        const started = performance.now();

        try {
            const data = await askAgent(question, threadId);
            const elapsed = Math.round(performance.now() - started);
            App.state.latestAgentResponse = data;
            App.state.latestCheckpoint = null;
            App.state.selectedCheckpoint = null;
            App.state.responseTimeMs = elapsed;
            renderSummary(data, elapsed);
            appendMessage("assistant", stripCitationMarkers(data.answer) || "No answer returned.", elapsed);
            App.emit("agent-response", data);

            App.state.latestCheckpoint = await loadCheckpoint(data.threadId);
            App.state.selectedCheckpoint = App.state.latestCheckpoint;
            App.emit("checkpoint-response", App.state.latestCheckpoint);
            App.state.checkpointHistory = await loadCheckpointHistory(data.threadId);
            App.emit("checkpoint-history-response", App.state.checkpointHistory);
        } catch (error) {
            App.setBadge("chat-status", "Failed");
            appendMessage("assistant", `Request failed: ${error.message}`);
        } finally {
            button.disabled = false;
        }
    }

    function renderWorkflow() {
        const response = App.state.latestAgentResponse;
        const checkpoint = App.state.selectedCheckpoint || App.state.latestCheckpoint;
        const labels = ["Query Planning", "Retrieval Agent", "Context Grading", "Answer Validation", "Grounding Score", "Completed"];
        const visited = (response && response.visited || []).map(value => String(value).toLowerCase());
        const failed = response && String(response.workflowStatus || "").toLowerCase().includes("fail");
        const container = App.byId("workflow-steps");
        container.replaceChildren();

        labels.forEach((label, index) => {
            const step = document.createElement("div");
            const known = visited.some(item => item.includes(label.split(" ")[0].toLowerCase()));
            const completed = response && (known || index < labels.length - 1 || !failed);
            step.className = `workflow-step ${failed && index === labels.length - 1 ? "failed" : completed ? "completed" : ""}`;
            step.innerHTML = `<i></i><strong>${label}</strong>`;
            container.appendChild(step);
        });

        App.setBadge("workflow-overall", response ? response.workflowStatus || "Completed" : "Awaiting chat request");
        const details = App.byId("workflow-details");
        if (!response && !checkpoint) {
            details.className = "data-grid empty-copy";
            details.textContent = "Select a persisted checkpoint or ask the agent a question to load checkpoint details.";
            return;
        }
        App.byId("workflow-thread-id").value = checkpoint && checkpoint.threadId || response && response.threadId || "";
        const values = {
            "Thread ID": checkpoint && checkpoint.threadId || response && response.threadId,
            "Checkpoint": checkpoint && checkpoint.checkpointId || response && response.checkpointId,
            "Context grade": response && response.contextGrade,
            "Validation": checkpoint && checkpoint.validationStatus || response && (response.validationOutcome || response.validationStatus),
            "Retrieval attempts": response && response.retrievalAttempts,
            "Rewrite attempts": response && response.rewriteAttempts,
            "Answer revisions": response && response.answerRevisionAttempts,
            "Strategy": checkpoint && checkpoint.retrievalStrategy
        };
        details.className = "data-grid";
        details.replaceChildren();
        Object.entries(values).forEach(([label, value]) => {
            const cell = document.createElement("div");
            cell.className = "data-cell";
            const key = document.createElement("span");
            const result = document.createElement("b");
            key.textContent = label;
            result.textContent = value ?? "--";
            cell.append(key, result);
            details.appendChild(cell);
        });
    }

    function detailRow(list, label, value) {
        const key = document.createElement("dt");
        const result = document.createElement("dd");
        key.textContent = label;
        result.textContent = value === null || value === undefined || value === "" ? "--" : value;
        list.append(key, result);
    }

    function renderWorkflowHistory() {
        const history = App.state.checkpointHistory.length
            ? App.state.checkpointHistory
            : App.state.globalCheckpointHistory || [];
        const container = App.byId("workflow-history");
        container.replaceChildren();
        if (!history.length) {
            container.textContent = "No persisted workflow runs returned for this thread.";
            return;
        }
        history.forEach(checkpoint => {
            const details = document.createElement("details");
            const summary = document.createElement("summary");
            const title = document.createElement("strong");
            const status = document.createElement("span");
            const list = document.createElement("dl");
            const review = document.createElement("button");
            details.className = "history-item";
            review.className = "history-review secondary-button";
            review.type = "button";
            review.textContent = "Review checkpoint";
            review.addEventListener("click", () => App.selectCheckpoint(checkpoint));
            summary.addEventListener("click", () => App.selectCheckpoint(checkpoint));
            title.textContent = checkpoint.userQuery || checkpoint.checkpointId || "Workflow run";
            status.textContent = `${checkpoint.workflowStatus || "--"} | ${checkpoint.createdAt ? new Date(checkpoint.createdAt).toLocaleString() : "--"}`;
            summary.append(title, status);
            detailRow(list, "Checkpoint", checkpoint.checkpointId);
            detailRow(list, "Thread", checkpoint.threadId);
            detailRow(list, "Normalized query", checkpoint.normalizedQuery);
            detailRow(list, "Rewritten query", checkpoint.rewrittenQuery);
            detailRow(list, "Retrieval strategy", checkpoint.retrievalStrategy);
            detailRow(list, "Validation", checkpoint.validationStatus);
            detailRow(list, "Grounding", checkpoint.groundingStatus);
            detailRow(list, "Retrieved documents", (checkpoint.retrievedDocumentIds || []).join(", ") || "None");
            detailRow(list, "Retrieved chunks", (checkpoint.retrievedChunkIds || []).join(", ") || "None");
            detailRow(list, "Answer", checkpoint.generatedAnswer);
            detailRow(list, "Error", checkpoint.errorMessage);
            details.append(summary, review, list);
            container.appendChild(details);
        });
    }

    async function loadWorkflowHistory() {
        const threadId = App.byId("workflow-thread-id").value.trim();
        if (!threadId) {
            App.byId("workflow-history-message").textContent = "Enter a thread ID before loading workflow runs.";
            return;
        }
        const button = App.byId("workflow-load-history");
        button.disabled = true;
        try {
            App.state.checkpointHistory = await fetchCheckpointHistory(threadId);
            App.byId("workflow-history-message").textContent = `Loaded ${App.state.checkpointHistory.length} persisted workflow run(s) for ${threadId}.`;
            renderWorkflowHistory();
        } catch (error) {
            App.byId("workflow-history-message").textContent = `Unable to load workflow runs: ${error.message}`;
        } finally {
            button.disabled = false;
        }
    }

    async function loadGlobalWorkflowHistory() {
        try {
            const response = await fetch("/api/dashboard/checkpoints?limit=100");
            if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
            App.state.globalCheckpointHistory = await response.json();
            App.byId("workflow-history-message").textContent = `Loaded ${App.state.globalCheckpointHistory.length} persisted workflow run(s) from the database. Enter a thread ID to filter.`;
            renderWorkflowHistory();
            App.emit("global-checkpoint-history-response", App.state.globalCheckpointHistory);
        } catch (error) {
            App.byId("workflow-history-message").textContent = `Unable to load persisted workflow runs: ${error.message}`;
        }
    }

    App.register(() => {
        App.byId("chat-form").addEventListener("submit", submit);
        App.byId("workflow-load-history").addEventListener("click", loadWorkflowHistory);
        document.addEventListener("agent-response", renderWorkflow);
        document.addEventListener("checkpoint-response", renderWorkflow);
        document.addEventListener("checkpoint-selection", renderWorkflow);
        document.addEventListener("checkpoint-history-response", () => {
            const threadId = App.state.latestAgentResponse && App.state.latestAgentResponse.threadId;
            App.byId("workflow-history-message").textContent = `Loaded ${App.state.checkpointHistory.length} persisted workflow run(s) for ${threadId || "the active thread"}.`;
            renderWorkflowHistory();
        });
        renderWorkflow();
        loadGlobalWorkflowHistory();
    });
}(window.DocumentETL));
