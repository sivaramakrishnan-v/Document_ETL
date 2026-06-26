package com.document.documentetl.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatAskRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesAndDeserializesDocumentIds() throws Exception {
        ChatAskRequest request = objectMapper.readValue(
                """
                        {"question":"Compare the docs","documentIds":[10,20]}
                        """,
                ChatAskRequest.class
        );

        assertEquals("Compare the docs", request.getQuestion());
        assertEquals(List.of(10L, 20L), request.getDocumentIds());

        String json = objectMapper.writeValueAsString(request);
        ChatAskRequest roundTrip = objectMapper.readValue(json, ChatAskRequest.class);

        assertEquals(List.of(10L, 20L), roundTrip.getDocumentIds());
    }
}
