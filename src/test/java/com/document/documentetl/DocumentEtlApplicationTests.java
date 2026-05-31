package com.document.documentetl;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class DocumentEtlApplicationTests {

    @MockBean(name = "generationChatLanguageModel")
    ChatLanguageModel generationChatLanguageModel;

    @MockBean(name = "evaluationChatLanguageModel")
    ChatLanguageModel evaluationChatLanguageModel;

    @Test
    void contextLoads() {
    }

}
