package com.document.documentetl.dto;

public class RagCheckpointRequestContext {

    private final String threadId;
    private final String checkpointNamespace;
    private final String userQuery;

    public RagCheckpointRequestContext(String threadId, String checkpointNamespace, String userQuery) {
        this.threadId = threadId;
        this.checkpointNamespace = checkpointNamespace;
        this.userQuery = userQuery;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getCheckpointNamespace() {
        return checkpointNamespace;
    }

    public String getUserQuery() {
        return userQuery;
    }
}
