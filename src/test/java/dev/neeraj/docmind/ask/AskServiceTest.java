package dev.neeraj.docmind.ask;

import static dev.neeraj.docmind.TestDocuments.IT_ONBOARDING;
import static dev.neeraj.docmind.TestDocuments.LEAVE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.neeraj.docmind.TestDocuments;
import dev.neeraj.docmind.ask.AskModels.AskResponse;
import dev.neeraj.docmind.ask.AskModels.Citation;
import dev.neeraj.docmind.ask.AskModels.Mode;
import dev.neeraj.docmind.config.DocMindProperties;
import dev.neeraj.docmind.index.ChunkIndex;
import dev.neeraj.docmind.index.SearchHit;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AskServiceTest {

    @TempDir
    Path tempDir;

    private ChunkIndex index;
    private final DocMindProperties properties = new DocMindProperties(
            Path.of("unused"),
            new DocMindProperties.Chunking(800, 150),
            new DocMindProperties.Retrieval(3, 5),
            new DocMindProperties.Anthropic("", "fake-model", 1024, "low", Duration.ofSeconds(5), 0));

    @BeforeEach
    void setUp() {
        index = new ChunkIndex(tempDir.resolve("index"));
        TestDocuments.index(index, LEAVE_POLICY, "leave");
        TestDocuments.index(index, IT_ONBOARDING, "it");
    }

    @AfterEach
    void tearDown() throws IOException {
        index.close();
    }

    @Test
    void generatedAnswerMapsCitationMarkersToRetrievedPassages() {
        FakeGenerator fake = new FakeGenerator((question, passages) ->
                "Full-time employees get 24 days [1], and up to 5 days carry over [1][2]. See also [9].");
        AskService service = new AskService(index, Optional.of(fake), properties);

        AskResponse response = service.ask("How many annual leave days do I get and how many carry forward?", null);

        assertThat(response.mode()).isEqualTo(Mode.GENERATED);
        assertThat(response.model()).isEqualTo("fake-model");
        assertThat(response.usage().inputTokens()).isEqualTo(100);
        assertThat(response.citations()).hasSize(3); // default top-k
        assertThat(response.citations()).extracting(Citation::number).containsExactly(1, 2, 3);
        assertThat(response.citations()).extracting(Citation::cited).containsExactly(true, true, false); // [9] ignored

        // Citation n must describe exactly the passage that was sent to the model as document n.
        List<SearchHit> sent = fake.lastPassages;
        for (int i = 0; i < sent.size(); i++) {
            Citation citation = response.citations().get(i);
            assertThat(citation.fileName()).isEqualTo(sent.get(i).fileName());
            assertThat(citation.chunkIndex()).isEqualTo(sent.get(i).chunkIndex());
            assertThat(citation.snippet()).isEqualTo(sent.get(i).text());
            assertThat(citation.score()).isEqualTo(sent.get(i).score());
        }
        assertThat(response.citations().getFirst().fileName()).isEqualTo("leave-policy.md");
    }

    @Test
    void fallsBackToRetrievalOnlyWithoutGenerator() {
        AskService service = new AskService(index, Optional.empty(), properties);

        AskResponse response = service.ask("How do I set up the VPN?", 2);

        assertThat(response.mode()).isEqualTo(Mode.RETRIEVAL_ONLY);
        assertThat(response.answer()).isEqualTo(AskService.RETRIEVAL_ONLY_MESSAGE);
        assertThat(response.model()).isNull();
        assertThat(response.usage()).isNull();
        assertThat(response.citations()).hasSize(2).noneMatch(Citation::cited);
        assertThat(response.citations().getFirst().fileName()).isEqualTo("it-onboarding-guide.txt");
        assertThat(service.generationEnabled()).isFalse();
    }

    @Test
    void doesNotCallTheModelWhenNothingMatches() {
        FakeGenerator fake = new FakeGenerator((q, p) -> "should not be called");
        AskService service = new AskService(index, Optional.of(fake), properties);

        AskResponse response = service.ask("quantum chromodynamics", null);

        assertThat(response.mode()).isEqualTo(Mode.NO_MATCH);
        assertThat(response.citations()).isEmpty();
        assertThat(fake.calls).isZero();
    }

    @Test
    void topKIsCappedByConfiguration() {
        FakeGenerator fake = new FakeGenerator((q, p) -> "ok");
        AskService service = new AskService(index, Optional.of(fake), properties);

        service.ask("leave employees days policy portal", 20);

        assertThat(fake.lastPassages).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void citedNumbersIgnoresOutOfRangeMarkers() {
        assertThat(AskService.citedNumbers("a [1] b [3] c [0] d [12] e [2]", 3)).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(AskService.citedNumbers("no citations here", 3)).isEmpty();
    }

    /** Deterministic stand-in for Claude that records what it was given. */
    private static final class FakeGenerator implements AnswerGenerator {
        private final BiFunction<String, List<SearchHit>, String> answer;
        private List<SearchHit> lastPassages = new ArrayList<>();
        private int calls;

        FakeGenerator(BiFunction<String, List<SearchHit>, String> answer) {
            this.answer = answer;
        }

        @Override
        public GeneratedAnswer generate(String question, List<SearchHit> passages) {
            calls++;
            lastPassages = List.copyOf(passages);
            return new GeneratedAnswer(answer.apply(question, passages), 100, 20);
        }

        @Override
        public String modelName() {
            return "fake-model";
        }
    }
}
