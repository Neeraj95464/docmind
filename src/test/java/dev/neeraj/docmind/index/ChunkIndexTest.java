package dev.neeraj.docmind.index;

import static dev.neeraj.docmind.TestDocuments.IT_ONBOARDING;
import static dev.neeraj.docmind.TestDocuments.LEAVE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.neeraj.docmind.TestDocuments;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkIndexTest {

    @TempDir
    Path tempDir;

    private ChunkIndex index;

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
    void ranksTheRelevantDocumentFirst() {
        List<SearchHit> leave = index.search("How many days of annual leave can be carried forward?", 3);
        assertThat(leave).isNotEmpty();
        assertThat(leave.getFirst().fileName()).isEqualTo("leave-policy.md");
        assertThat(leave.getFirst().text()).contains("carried forward");

        List<SearchHit> vpn = index.search("When does the VPN disconnect?", 3);
        assertThat(vpn.getFirst().fileName()).isEqualTo("it-onboarding-guide.txt");
        assertThat(vpn.getFirst().text()).contains("12 hours");
    }

    @Test
    void stemmingMatchesWordVariants() {
        // "passwords" in the question, "Passwords"/"password" in the text; "changing" vs "changed".
        List<SearchHit> hits = index.search("password changing rules", 1);
        assertThat(hits.getFirst().text()).contains("180 days");
    }

    @Test
    void scoresAreDescendingAndTopKIsRespected() {
        List<SearchHit> hits = index.search("leave days employees", 3);
        assertThat(hits).hasSizeLessThanOrEqualTo(3);
        assertThat(hits).extracting(SearchHit::score).isSortedAccordingTo((a, b) -> Float.compare(b, a));
    }

    @Test
    void queryTextIsEscapedAndUnmatchedQueriesReturnNothing() {
        assertThat(index.search("annual AND (leave OR \"sick\"* ~ ^ :", 3)).isNotEmpty();
        assertThat(index.search("quantum chromodynamics", 3)).isEmpty();
        assertThat(index.search("the and of", 3)).isEmpty(); // only stop words
        assertThat(index.search("  ", 3)).isEmpty();
    }

    @Test
    void listsAndDeletesDocuments() {
        assertThat(index.list()).extracting(DocumentInfo::id).containsExactlyInAnyOrder("leave", "it");
        assertThat(index.find("leave")).get().extracting(DocumentInfo::fileName).isEqualTo("leave-policy.md");

        assertThat(index.delete("leave")).isTrue();
        assertThat(index.delete("leave")).isFalse();
        assertThat(index.list()).extracting(DocumentInfo::id).containsExactly("it");
        assertThat(index.search("annual leave carried forward", 5))
                .allSatisfy(hit -> assertThat(hit.documentId()).isEqualTo("it"));
    }

    @Test
    void indexIsPersistedAcrossRestarts() throws IOException {
        index.close();
        index = new ChunkIndex(tempDir.resolve("index"));

        assertThat(index.list()).hasSize(2);
        assertThat(index.search("multi-factor authentication", 1).getFirst().fileName())
                .isEqualTo("it-onboarding-guide.txt");
    }
}
