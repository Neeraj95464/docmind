package dev.neeraj.docmind.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * All tunables in one place. Defaults are deliberately conservative to keep token spend low.
 */
@Validated
@ConfigurationProperties(prefix = "docmind")
public record DocMindProperties(
        @NotNull @DefaultValue("./data") Path dataDir,
        @Valid @NotNull @DefaultValue Chunking chunking,
        @Valid @NotNull @DefaultValue Retrieval retrieval,
        @Valid @NotNull @DefaultValue Anthropic anthropic) {

    /** Chunk sizes are measured in characters (roughly 4 characters per token for English text). */
    public record Chunking(
            @Min(200) @Max(8000) @DefaultValue("800") int size,
            @Min(0) @DefaultValue("150") int overlap) {

        @AssertTrue(message = "docmind.chunking.overlap must be smaller than docmind.chunking.size")
        public boolean isOverlapSmallerThanSize() {
            return overlap < size;
        }
    }

    public record Retrieval(
            @Min(1) @DefaultValue("4") int defaultTopK,
            @Min(1) @Max(20) @DefaultValue("8") int maxTopK) {
    }

    /**
     * Claude settings. The API key is read from {@code ANTHROPIC_API_KEY}; when it is blank the
     * application runs in retrieval-only mode.
     */
    public record Anthropic(
            @DefaultValue("") String apiKey,
            @NotBlank @DefaultValue("claude-sonnet-5-5") String model,
            @Min(256) @Max(16000) @DefaultValue("2048") long maxTokens,
            @NotBlank @DefaultValue("low") String effort,
            @NotNull @DefaultValue("30s") Duration timeout,
            @Min(0) @Max(5) @DefaultValue("2") int maxRetries) {
    }
}
