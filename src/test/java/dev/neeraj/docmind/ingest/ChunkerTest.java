package dev.neeraj.docmind.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    @Test
    void blankTextProducesNoChunks() {
        Chunker chunker = new Chunker(200, 50);
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   \n\n ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void shortTextIsASingleNormalisedChunk() {
        List<String> chunks = new Chunker(200, 50).chunk("Hello   world.\n\nSecond   line here.");
        assertThat(chunks).containsExactly("Hello world. Second line here.");
    }

    @Test
    void chunksRespectMaximumSizeAndSentenceBoundaries() {
        String text = sentences(40);
        List<String> chunks = new Chunker(200, 60).chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.length()).isLessThanOrEqualTo(200);
            assertThat(chunk).startsWith("Sentence").endsWith(".");
        });
    }

    @Test
    void consecutiveChunksOverlap() {
        List<String> chunks = new Chunker(200, 60).chunk(sentences(40));

        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String firstSentenceOfNext = chunks.get(i).substring(0, chunks.get(i).indexOf('.') + 1);
            assertThat(previous).as("chunk %d should repeat the tail of chunk %d", i, i - 1)
                    .endsWith(firstSentenceOfNext);
        }
    }

    @Test
    void zeroOverlapDoesNotRepeatContent() {
        String text = sentences(30);
        List<String> chunks = new Chunker(200, 0).chunk(text);
        assertThat(String.join(" ", chunks)).isEqualTo(text);
    }

    @Test
    void overlongSentenceIsHardSplitWithOverlap() {
        String longWord = "x".repeat(450);
        List<String> chunks = new Chunker(200, 50).chunk("Intro sentence. " + longWord + ". Outro sentence.");

        assertThat(chunks).first().isEqualTo("Intro sentence.");
        assertThat(chunks).last().isEqualTo("Outro sentence.");
        List<String> pieces = chunks.subList(1, chunks.size() - 1);
        assertThat(pieces).allSatisfy(piece -> assertThat(piece.length()).isLessThanOrEqualTo(200));
        assertThat(pieces.stream().mapToInt(String::length).sum()).isGreaterThan(450); // overlap repeats content
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new Chunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chunker(100, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String sentences(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "Sentence number " + i + " talks about topic " + (i * 7) + ".")
                .collect(Collectors.joining(" "));
    }
}
