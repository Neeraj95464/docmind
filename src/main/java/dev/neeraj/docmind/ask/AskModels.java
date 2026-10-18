package dev.neeraj.docmind.ask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request/response types for the ask endpoint. */
public final class AskModels {

    private AskModels() {
    }

    public record AskRequest(
            @Schema(example = "How many days of annual leave do full-time employees get?")
            @NotBlank @Size(max = 1000) String question,
            @Schema(description = "Number of passages to retrieve; defaults to docmind.retrieval.default-top-k")
            @Min(1) @Max(20) Integer topK) {
    }

    public enum Mode {
        /** Claude produced an answer grounded in the cited passages. */
        GENERATED,
        /** No API key configured: the best-matching passages are returned instead of an answer. */
        RETRIEVAL_ONLY,
        /** Nothing in the index matched the question; the model was not called. */
        NO_MATCH
    }

    public record Citation(
            @Schema(description = "Number used in the answer, e.g. 1 for [1]") int number,
            String documentId,
            String fileName,
            int chunkIndex,
            String snippet,
            float score,
            @Schema(description = "Whether the generated answer referenced this passage") boolean cited) {
    }

    public record Usage(long inputTokens, long outputTokens) {
    }

    public record AskResponse(
            String answer,
            Mode mode,
            @Schema(nullable = true) String model,
            List<Citation> citations,
            @Schema(nullable = true) Usage usage) {
    }
}
