package com.smartpay.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Uses Spring AI only to improve wording. Any failure returns deterministic mock wording. */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai")
public class SpringAiExplanationRewriter implements ExplanationRewriter {

    private static final Logger log = LoggerFactory.getLogger(SpringAiExplanationRewriter.class);

    private final ChatClient chatClient;
    private final long timeoutSeconds;

    public SpringAiExplanationRewriter(ChatModel chatModel,
                                       @Value("${app.ai.timeout-seconds:5}") long timeoutSeconds) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.timeoutSeconds = Math.max(1L, timeoutSeconds);
    }

    @Override
    public Result rewrite(String deterministicExplanation, int score, String riskLevel) {
        try {
            String content = CompletableFuture.supplyAsync(() -> chatClient.prompt()
                            .system("You rewrite fraud explanation wording for a demo wallet. "
                                    + "Return only plain text under 300 characters. Never approve, reject, "
                                    + "hold, release, debit, credit, or change any score, level, amount, or "
                                    + "decision. Preserve all numbers and the risk level exactly.")
                            .user("Deterministic explanation: " + deterministicExplanation
                                    + "\nScore: " + score + "/100\nRisk level: " + riskLevel)
                            .call()
                            .content())
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Empty AI response");
            }
            String cleaned = clean(content);
            String upper = cleaned.toUpperCase(Locale.ROOT);
            if (upper.contains("I APPROVE") || upper.contains("I REJECT") || upper.contains("RELEASE THE")) {
                throw new IllegalStateException("AI wording attempted to change a decision");
            }
            return new Result(cleaned, "OPENAI");
        } catch (Exception exception) {
            log.warn("Spring AI failed; using deterministic fallback: {}", exception.getMessage());
            return new Result(fallback(deterministicExplanation, score, riskLevel), "MOCK_FALLBACK");
        }
    }

    private String fallback(String deterministic, int score, String level) {
        return "Simulated AI wording: " + (deterministic == null ? "" : deterministic.trim())
                + " (Score " + score + "/100, " + level + ". AI unavailable; no decision changed.)";
    }

    private String clean(String value) {
        String cleaned = value.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^\"|\"$", "");
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }
}
