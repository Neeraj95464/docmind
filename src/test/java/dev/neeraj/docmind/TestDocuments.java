package dev.neeraj.docmind;

import dev.neeraj.docmind.index.ChunkIndex;
import dev.neeraj.docmind.index.DocumentInfo;
import dev.neeraj.docmind.ingest.Chunker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Helpers for tests that need the sample documents from {@code samples/}. */
public final class TestDocuments {

    public static final Path LEAVE_POLICY = Path.of("samples", "leave-policy.md");
    public static final Path IT_ONBOARDING = Path.of("samples", "it-onboarding-guide.txt");

    private TestDocuments() {
    }

    public static byte[] bytes(Path sample) {
        try {
            return Files.readAllBytes(sample);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Indexes a sample directly (bypassing the service) and returns its metadata. */
    public static DocumentInfo index(ChunkIndex index, Path sample, String id) {
        String text = new String(bytes(sample), StandardCharsets.UTF_8);
        List<String> chunks = new Chunker(500, 100).chunk(text);
        DocumentInfo info = new DocumentInfo(id, sample.getFileName().toString(), chunks.size(), text.length(), Instant.now());
        index.add(info, chunks);
        return info;
    }
}
