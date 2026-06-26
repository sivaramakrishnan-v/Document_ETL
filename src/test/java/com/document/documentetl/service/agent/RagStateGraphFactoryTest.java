package com.document.documentetl.service.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagStateGraphFactoryTest {

    @Test
    void detectsComparisonQuestions() {
        assertTrue(RagStateGraphFactory.isComparisonQuestion("Compare the advice across documents"));
        assertTrue(RagStateGraphFactory.isComparisonQuestion("What differences are between documents?"));
        assertFalse(RagStateGraphFactory.isComparisonQuestion("What is a credit score?"));
    }

    @Test
    void detectsDetailedQuestions() {
        assertTrue(RagStateGraphFactory.isDetailedQuestion("Give me a detailed explanation"));
        assertTrue(RagStateGraphFactory.isDetailedQuestion("Explain fully how this works"));
        assertTrue(RagStateGraphFactory.isDetailedQuestion("I need a comprehensive answer"));
        assertFalse(RagStateGraphFactory.isDetailedQuestion("What is a credit score?"));
    }

    @Test
    void simpleQuestionPromptDefaultsToConciseGroundedAnswer() {
        String prompt = RagStateGraphFactory.buildAnswerPrompt(
                "What is a credit score?",
                "doc=1 text=Credit scores estimate repayment likelihood."
        );

        assertTrue(prompt.contains("answer in 1 to 2 short paragraphs"));
        assertTrue(prompt.contains("usually 150 to 300 words"));
        assertTrue(prompt.contains("Answer the user question using only the evidence below"));
        assertTrue(prompt.contains("Do not introduce outside knowledge"));
        assertTrue(prompt.contains("uploaded documents do not provide enough information"));
        assertTrue(prompt.contains("not internal document IDs"));
        assertFalse(prompt.contains("This is a comparison or cross-document question"));
    }

    @Test
    void comparisonPromptIncludesConciseComparisonInstructions() {
        String prompt = RagStateGraphFactory.buildAnswerPrompt(
                "Compare the documents",
                "doc=1 text=A\ndoc=2 text=B"
        );

        assertTrue(prompt.contains("Mention the main similarity first"));
        assertTrue(prompt.contains("Then mention the key differences clearly"));
        assertTrue(prompt.contains("End with a short synthesized conclusion"));
        assertTrue(prompt.contains("Do not force comparison answers into tables"));
        assertTrue(prompt.contains("Do not include long source-by-source evidence sections"));
        assertTrue(prompt.contains("Do not introduce outside knowledge"));
    }

    @Test
    void detailedQuestionPromptAllowsLongerStructuredAnswer() {
        String prompt = RagStateGraphFactory.buildAnswerPrompt(
                "Explain fully how budgeting helps prevent debt",
                "doc=1 text=Budgeting can identify spending and saving goals."
        );

        assertTrue(prompt.contains("The user asked for a detailed or comprehensive answer"));
        assertTrue(prompt.contains("Provide a longer structured answer when the evidence supports it"));
        assertTrue(prompt.contains("Organize the answer clearly with concise headings or bullets as useful"));
        assertTrue(prompt.contains("Do not introduce outside knowledge"));
        assertTrue(prompt.contains("uploaded documents do not provide enough information"));
    }
}
