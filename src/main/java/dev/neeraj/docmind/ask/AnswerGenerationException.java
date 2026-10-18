package dev.neeraj.docmind.ask;

/** The upstream LLM call failed; surfaced to clients as 502 Bad Gateway. */
public class AnswerGenerationException extends RuntimeException {

    public AnswerGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
