package com.document.documentetl.service.grounding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundingScoreServiceTest {

    private final GroundingScoreService service = new GroundingScoreService();

    @Test
    void fullyGroundedAnswerReturnsGrounded() {
        GroundingScoreResult result = service.calculate(
                "Payment history has the largest impact on a credit score. Credit utilization also affects score significantly.",
                List.of("[doc=1]", "[doc=2]"),
                List.of(
                        "doc=1 score=0.9000 text=Payment history is the largest factor in many credit scoring models.",
                        "doc=2 score=0.8500 text=Credit utilization ratio strongly affects score and lending risk."
                )
        );

        assertEquals(1.0d, result.getGroundednessScore());
        assertEquals(GroundingStatus.GROUNDED, result.getGroundingStatus());
        assertEquals(0, result.getUnsupportedClaimsCount());
    }

    @Test
    void partiallySupportedAnswerReturnsPartiallyGrounded() {
        GroundingScoreResult result = service.calculate(
                "Payment history is important for credit score. The stock market rose by twenty percent this year.",
                List.of("[doc=1]"),
                List.of("doc=1 score=0.9000 text=Payment history is one of the biggest contributors to a credit score.")
        );

        assertEquals(0.5d, result.getGroundednessScore());
        assertEquals(GroundingStatus.PARTIALLY_GROUNDED, result.getGroundingStatus());
        assertEquals(1, result.getUnsupportedClaimsCount());
    }

    @Test
    void unsupportedAnswerReturnsWeaklyGrounded() {
        GroundingScoreResult result = service.calculate(
                "Mars has liquid oceans and supports human cities right now.",
                List.of("[doc=9]"),
                List.of("doc=9 score=0.7000 text=Credit card utilization should ideally remain below thirty percent.")
        );

        assertEquals(0.0d, result.getGroundednessScore());
        assertEquals(GroundingStatus.WEAKLY_GROUNDED, result.getGroundingStatus());
        assertEquals(1, result.getUnsupportedClaimsCount());
    }

    @Test
    void emptyContextDoesNotCrash() {
        GroundingScoreResult result = service.calculate(
                "Credit scores influence loan approvals.",
                List.of(),
                List.of()
        );

        assertEquals(0.0d, result.getCitationCoverageScore());
        assertTrue(result.getGroundednessScore() >= 0.0d);
        assertTrue(result.getGroundingStatus() != null);
    }
}
