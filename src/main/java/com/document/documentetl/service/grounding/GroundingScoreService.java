package com.document.documentetl.service.grounding;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GroundingScoreService {

    private static final Pattern DOC_ID_PATTERN = Pattern.compile("doc=(\\d+)");
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z0-9]+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "for", "to", "of", "in", "on", "at", "by",
            "with", "from", "as", "is", "are", "was", "were", "be", "been", "being", "it", "its", "this", "that",
            "these", "those", "there", "their", "they", "them", "we", "our", "you", "your", "i", "me", "my", "he",
            "she", "his", "her", "do", "does", "did", "have", "has", "had", "can", "could", "should", "would",
            "may", "might", "will", "shall", "than", "very", "also", "about", "into", "over", "under", "more",
            "most", "such", "not", "no", "yes", "so", "because", "while", "during", "after", "before"
    );

    private static final double SUPPORT_THRESHOLD = 0.55d;
    private static final int MIN_IMPORTANT_TERMS = 3;

    public GroundingScoreResult calculate(String generatedAnswer,
                                          List<String> citations,
                                          List<String> retrievedContext) {
        List<String> sentences = splitIntoSentences(generatedAnswer);
        List<String> candidateContext = resolveCandidateContext(citations, retrievedContext);
        Set<String> contextTerms = extractContextTerms(candidateContext);

        int totalSentenceCount = 0;
        int supportedSentenceCount = 0;

        for (String sentence : sentences) {
            List<String> importantTerms = extractImportantTerms(sentence);
            if (importantTerms.size() < MIN_IMPORTANT_TERMS) {
                continue;
            }
            totalSentenceCount++;
            if (isSupported(importantTerms, contextTerms)) {
                supportedSentenceCount++;
            }
        }

        int unsupportedClaimsCount = Math.max(0, totalSentenceCount - supportedSentenceCount);
        boolean hasAnswerText = generatedAnswer != null && !generatedAnswer.isBlank();
        double groundednessScore = totalSentenceCount == 0
                ? (hasAnswerText ? 1.0d : 0.0d)
                : (double) supportedSentenceCount / totalSentenceCount;

        int totalRetrievedChunks = retrievedContext == null ? 0 : retrievedContext.size();
        int citedChunksUsed = countCitedChunksUsed(citations, retrievedContext);
        double citationCoverageScore = totalRetrievedChunks == 0
                ? 0.0d
                : (double) citedChunksUsed / totalRetrievedChunks;

        groundednessScore = round4(groundednessScore);
        citationCoverageScore = round4(citationCoverageScore);
        GroundingStatus groundingStatus = toStatus(groundednessScore);

        return new GroundingScoreResult(
                groundednessScore,
                citationCoverageScore,
                unsupportedClaimsCount,
                groundingStatus,
                supportedSentenceCount,
                totalSentenceCount
        );
    }

    private static List<String> splitIntoSentences(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        String normalized = answer.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] raw = normalized.split("(?<=[.!?])\\s+");
        List<String> sentences = new ArrayList<>();
        for (String sentence : raw) {
            String trimmed = sentence == null ? "" : sentence.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private static List<String> resolveCandidateContext(List<String> citations, List<String> retrievedContext) {
        if (retrievedContext == null || retrievedContext.isEmpty()) {
            return List.of();
        }

        Set<Long> citedDocIds = extractCitedDocIds(citations);
        if (citedDocIds.isEmpty()) {
            return retrievedContext;
        }

        List<String> filtered = new ArrayList<>();
        for (String chunk : retrievedContext) {
            Long docId = extractDocId(chunk);
            if (docId != null && citedDocIds.contains(docId)) {
                filtered.add(chunk);
            }
        }
        return filtered.isEmpty() ? retrievedContext : filtered;
    }

    private static boolean isSupported(List<String> importantTerms, Set<String> contextTerms) {
        if (importantTerms.isEmpty()) {
            return true;
        }
        if (contextTerms.isEmpty()) {
            return false;
        }

        int matched = 0;
        for (String term : importantTerms) {
            if (contextTerms.contains(term)) {
                matched++;
            }
        }
        double overlap = (double) matched / importantTerms.size();
        return overlap >= SUPPORT_THRESHOLD;
    }

    private static Set<String> extractContextTerms(List<String> contextChunks) {
        Set<String> terms = new HashSet<>();
        for (String chunk : contextChunks) {
            terms.addAll(extractImportantTerms(extractChunkText(chunk)));
        }
        return terms;
    }

    private static String extractChunkText(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return "";
        }
        int textIndex = chunk.indexOf("text=");
        if (textIndex < 0) {
            return chunk;
        }
        return chunk.substring(textIndex + 5);
    }

    private static int countCitedChunksUsed(List<String> citations, List<String> retrievedContext) {
        if (retrievedContext == null || retrievedContext.isEmpty() || citations == null || citations.isEmpty()) {
            return 0;
        }
        Set<Long> citedDocIds = extractCitedDocIds(citations);
        if (citedDocIds.isEmpty()) {
            return 0;
        }
        int used = 0;
        for (String chunk : retrievedContext) {
            Long docId = extractDocId(chunk);
            if (docId != null && citedDocIds.contains(docId)) {
                used++;
            }
        }
        return used;
    }

    private static Set<Long> extractCitedDocIds(List<String> citations) {
        Set<Long> citedDocIds = new LinkedHashSet<>();
        if (citations == null) {
            return citedDocIds;
        }
        for (String citation : citations) {
            Long docId = extractDocId(citation);
            if (docId != null) {
                citedDocIds.add(docId);
            }
        }
        return citedDocIds;
    }

    private static Long extractDocId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DOC_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    private static List<String> extractImportantTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = NON_ALPHANUMERIC.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
        Matcher matcher = WORD_PATTERN.matcher(normalized);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 3) {
                continue;
            }
            if (STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
        }
        return new ArrayList<>(terms);
    }

    private static GroundingStatus toStatus(double groundednessScore) {
        if (groundednessScore >= 0.80d) {
            return GroundingStatus.GROUNDED;
        }
        if (groundednessScore >= 0.50d) {
            return GroundingStatus.PARTIALLY_GROUNDED;
        }
        return GroundingStatus.WEAKLY_GROUNDED;
    }

    private static double round4(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
