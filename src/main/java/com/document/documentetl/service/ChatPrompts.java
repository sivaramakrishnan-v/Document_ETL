package com.document.documentetl.service;

public final class ChatPrompts {

    public static final String ANSWER_FROM_CONTEXT_TEMPLATE = """
            Answer the following question using the provided context. If the answer is not in the context, politely state that you do not have enough information.
            Context: {{context}}
            Question: {{question}}
            """;

    private ChatPrompts() {
    }
}
