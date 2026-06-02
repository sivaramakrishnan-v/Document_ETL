(function (App) {
    "use strict";

    const STAGES = ["Upload", "Parse", "Chunk", "Kafka Event", "Embedding", "pgvector Storage"];

    function stageStatus(index, status) {
        if (!status) return index === 0 ? "waiting" : "pending";
        const documents = status.documents || {};
        const embeddings = status.embeddings || {};
        const events = status.events || {};
        if (documents.failed || (status.embeddingJobs || {}).failed || events.failed) return "failed";
        if (index === 0 && documents.total) return "completed";
        if (index === 1 && (documents.chunked || documents.completed)) return "completed";
        if (index === 2 && embeddings.chunks) return "completed";
        if (index === 3 && events.processed) return "completed";
        if (index === 4 && embeddings.embeddings) return embeddings.missingEmbeddings ? "in progress" : "completed";
        if (index === 5 && embeddings.embeddings && !embeddings.missingEmbeddings) return "completed";
        return "pending";
    }

    function renderStages(status) {
        const container = App.byId("etl-stages");
        container.replaceChildren();
        STAGES.forEach((name, index) => {
            const value = stageStatus(index, status);
            const stage = document.createElement("div");
            stage.className = "pipeline-stage";
            const label = document.createElement("strong");
            const badge = document.createElement("span");
            label.textContent = name;
            badge.className = `status-badge ${App.badgeClass(value)}`;
            badge.textContent = value;
            stage.append(label, badge);
            container.appendChild(stage);
        });
    }

    function renderCounts(status) {
        const counts = App.byId("etl-counts");
        counts.className = "data-grid";
        counts.replaceChildren();
        Object.entries(status || {}).filter(([, value]) => value && !Array.isArray(value) && typeof value === "object")
            .forEach(([group, values]) => Object.entries(values).forEach(([label, value]) => {
                const cell = document.createElement("div");
                cell.className = "data-cell";
                const key = document.createElement("span");
                const result = document.createElement("b");
                key.textContent = `${group}: ${label}`;
                result.textContent = value;
                cell.append(key, result);
                counts.appendChild(cell);
            }));
    }

    function renderEvents(events) {
        const container = App.byId("etl-events");
        container.replaceChildren();
        if (!events || !events.length) {
            container.textContent = "No recent events returned.";
            return;
        }
        events.forEach(event => {
            const item = document.createElement("div");
            item.className = "event-item";
            const title = document.createElement("b");
            title.textContent = event.eventType || "Pipeline event";
            item.append(title, document.createElement("br"), `${event.processingStatus || "--"} | ${event.documentId || "no document id"}`);
            container.appendChild(item);
        });
    }

    async function fetchJson(url) {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
        return response.json();
    }

    async function refreshStatus() {
        const button = App.byId("etl-refresh");
        button.disabled = true;
        try {
            const status = await fetchJson("/api/etl/v2/status");
            renderStages(status);
            renderCounts(status);
            renderEvents(status.recentEvents);
            App.byId("etl-debug").textContent = App.json(status);
            App.byId("etl-message").textContent = "Pipeline status refreshed.";
        } catch (error) {
            App.byId("etl-message").textContent = `Unable to load ETL status: ${error.message}`;
        } finally {
            button.disabled = false;
        }
    }

    async function runAction(buttonId, url, message) {
        const button = App.byId(buttonId);
        button.disabled = true;
        try {
            await fetchJson(url);
            App.byId("etl-message").textContent = message;
            await refreshStatus();
        } catch (error) {
            App.byId("etl-message").textContent = `ETL action failed: ${error.message}`;
        } finally {
            button.disabled = false;
        }
    }

    async function uploadDocument() {
        // TODO: Replace with FormData POST when the backend exposes a browser upload endpoint.
        const file = App.byId("etl-file").files[0];
        App.byId("etl-message").textContent = file
            ? `Upload endpoint is not available. "${file.name}" has not been sent.`
            : "Select a document before uploading.";
    }

    App.register(() => {
        renderStages(null);
        App.byId("etl-refresh").addEventListener("click", refreshStatus);
        App.byId("etl-upload").addEventListener("click", uploadDocument);
        App.byId("etl-stage-local").addEventListener("click", () => runAction("etl-stage-local", "/api/etl/v2/stage", "Server documents staged."));
        App.byId("etl-reconcile").addEventListener("click", () => runAction("etl-reconcile", "/api/etl/v2/reconcile-embeddings", "Embedding reconciliation submitted."));
    });
}(window.DocumentETL));
