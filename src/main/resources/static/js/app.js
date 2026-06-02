(function () {
    "use strict";

    const hooks = [];
    const state = {
        latestAgentResponse: null,
        latestCheckpoint: null,
        selectedCheckpoint: null,
        checkpointHistory: [],
        globalCheckpointHistory: [],
        responseTimeMs: null
    };

    function byId(id) {
        return document.getElementById(id);
    }

    function setText(id, value, fallback = "--") {
        const element = byId(id);
        if (element) {
            element.textContent = value === null || value === undefined || value === "" ? fallback : value;
        }
    }

    function formatScore(value) {
        if (value === null || value === undefined || Number.isNaN(Number(value))) {
            return "--";
        }
        const number = Number(value);
        return `${number <= 1 ? Math.round(number * 100) : Math.round(number)}%`;
    }

    function badgeClass(status) {
        const normalized = String(status || "").toLowerCase();
        if (normalized.includes("fail") || normalized.includes("error")) return "failed";
        if (normalized.includes("complete") || normalized.includes("pass") || normalized.includes("ground")) return "success";
        if (normalized.includes("progress") || normalized.includes("pending") || normalized.includes("running")) return "warning";
        return "neutral";
    }

    function setBadge(id, text) {
        const badge = byId(id);
        if (!badge) return;
        badge.textContent = text || "Unknown";
        badge.className = `status-badge ${badgeClass(text)}`;
    }

    function json(value) {
        return JSON.stringify(value || {}, null, 2);
    }

    function emit(name, detail) {
        document.dispatchEvent(new CustomEvent(name, { detail }));
    }

    function register(init) {
        hooks.push(init);
    }

    function selectCheckpoint(checkpoint) {
        state.selectedCheckpoint = checkpoint || null;
        emit("checkpoint-selection", state.selectedCheckpoint);
    }

    function navigate(panelId) {
        document.querySelectorAll(".panel").forEach(panel => panel.classList.toggle("active", panel.id === panelId));
        document.querySelectorAll(".nav-item").forEach(item => item.classList.toggle("active", item.dataset.panel === panelId));
        const active = document.querySelector(`.nav-item[data-panel="${panelId}"]`);
        setText("page-title", active ? active.textContent.replace(/^\d+/, "").trim() : "DocumentETL");
    }

    window.DocumentETL = { state, byId, setText, formatScore, badgeClass, setBadge, json, emit, register, navigate, selectCheckpoint };

    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll(".nav-item").forEach(item => item.addEventListener("click", () => navigate(item.dataset.panel)));
        hooks.forEach(init => init());
    });
}());
