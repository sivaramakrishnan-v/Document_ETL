package com.document.documentetl.service.grounding;

public class GroundingScoreResult {

    private final double groundednessScore;
    private final double citationCoverageScore;
    private final int unsupportedClaimsCount;
    private final GroundingStatus groundingStatus;
    private final int supportedSentenceCount;
    private final int totalSentenceCount;

    public GroundingScoreResult(double groundednessScore,
                                double citationCoverageScore,
                                int unsupportedClaimsCount,
                                GroundingStatus groundingStatus,
                                int supportedSentenceCount,
                                int totalSentenceCount) {
        this.groundednessScore = groundednessScore;
        this.citationCoverageScore = citationCoverageScore;
        this.unsupportedClaimsCount = unsupportedClaimsCount;
        this.groundingStatus = groundingStatus;
        this.supportedSentenceCount = supportedSentenceCount;
        this.totalSentenceCount = totalSentenceCount;
    }

    public double getGroundednessScore() {
        return groundednessScore;
    }

    public double getCitationCoverageScore() {
        return citationCoverageScore;
    }

    public int getUnsupportedClaimsCount() {
        return unsupportedClaimsCount;
    }

    public GroundingStatus getGroundingStatus() {
        return groundingStatus;
    }

    public int getSupportedSentenceCount() {
        return supportedSentenceCount;
    }

    public int getTotalSentenceCount() {
        return totalSentenceCount;
    }
}
