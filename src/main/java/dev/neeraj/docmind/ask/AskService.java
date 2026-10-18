package dev.neeraj.docmind.ask;

import dev.neeraj.docmind.ask.AnswerGenerator.GeneratedAnswer;
import dev.neeraj.docmind.ask.AskModels.AskResponse;
import dev.neeraj.docmind.ask.AskModels.Citation;
import dev.neeraj.docmind.ask.AskModels.Mode;
import dev.neeraj.docmind.ask.AskModels.Usage;
import dev.neeraj.docmind.config.DocMindProperties;
import dev.neeraj.docmind.index.ChunkIndex;
import dev.neeraj.docmind.index.SearchHit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Orchestrates retrieve -> (optionally) generate -> map citations. */
@Service
public class AskService {

    static final String RETRIEVAL_ONLY_MESSAGE =
            "Retrieval-only mode: ANTHROPIC_API_KEY is not set, so no answer was generated. "
                    + "These are the most relevant passages from your documents.";
    static final String NO_MATCH_MESSAGE =
            "I don't know based on the provided documents - nothing in the index matches this question.";

    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,2})]");

    private final ChunkIndex index;
    private final Optional<AnswerGenerator> generator;
    private final DocMindProperties.Retrieval retrieval;

    public AskService(ChunkIndex index, Optional<AnswerGenerator> generator, DocMindProperties properties) {
        this.index = index;
        this.generator = generator;
        this.retrieval = properties.retrieval();
    }

    public boolean generationEnabled() {
        return generator.isPresent();
    }

    public AskResponse ask(String question, Integer requestedTopK) {
        int topK = Math.min(requestedTopK == null ? retrieval.defaultTopK() : requestedTopK, retrieval.maxTopK());
        List<SearchHit> hits = index.search(question, topK);

        if (hits.isEmpty()) {
            return new AskResponse(NO_MATCH_MESSAGE, Mode.NO_MATCH, null, List.of(), null);
        }
        if (generator.isEmpty()) {
            return new AskResponse(RETRIEVAL_ONLY_MESSAGE, Mode.RETRIEVAL_ONLY, null, citations(hits, Set.of()), null);
        }

        AnswerGenerator llm = generator.get();
        GeneratedAnswer answer = llm.generate(question, hits);
        return new AskResponse(
                answer.text(),
                Mode.GENERATED,
                llm.modelName(),
                citations(hits, citedNumbers(answer.text(), hits.size())),
                new Usage(answer.inputTokens(), answer.outputTokens()));
    }

    /** Extracts the [n] markers that refer to passages that were actually supplied. */
    static Set<Integer> citedNumbers(String answer, int passageCount) {
        Set<Integer> cited = new HashSet<>();
        Matcher matcher = CITATION_MARKER.matcher(answer);
        while (matcher.find()) {
            int n = Integer.parseInt(matcher.group(1));
            if (n >= 1 && n <= passageCount) {
                cited.add(n);
            }
        }
        return cited;
    }

    private static List<Citation> citations(List<SearchHit> hits, Set<Integer> cited) {
        List<Citation> citations = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            int number = i + 1;
            citations.add(new Citation(number, hit.documentId(), hit.fileName(), hit.chunkIndex(),
                    hit.text(), hit.score(), cited.contains(number)));
        }
        return citations;
    }
}
