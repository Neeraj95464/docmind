package dev.neeraj.docmind.ask;

import dev.neeraj.docmind.index.SearchHit;
import java.util.List;

/**
 * Turns a question plus retrieved passages into a grounded answer. The passages are numbered
 * from 1 in list order and the answer cites them as [1], [2], ...
 *
 * <p>Kept as an interface so the LLM provider is swappable and tests can use a deterministic fake.
 */
public interface AnswerGenerator {

    GeneratedAnswer generate(String question, List<SearchHit> passages);

    /** Name of the model that produced answers, for display and logging. */
    String modelName();

    record GeneratedAnswer(String text, long inputTokens, long outputTokens) {
    }
}
