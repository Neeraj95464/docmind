package dev.neeraj.docmind.ingest;

import static dev.neeraj.docmind.TestDocuments.LEAVE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.neeraj.docmind.TestDocuments;
import dev.neeraj.docmind.config.DocMindProperties;
import dev.neeraj.docmind.index.ChunkIndex;
import dev.neeraj.docmind.index.DocumentInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.FileSystemUtils;

class DocumentServiceTest {

    @TempDir
    Path dataDir;

    private final TextExtractor extractor = new TextExtractor();
    private final Chunker chunker = new Chunker(500, 100);

    @Test
    void ingestStoresOriginalAndIndexesChunks() throws IOException {
        try (ChunkIndex index = new ChunkIndex(dataDir.resolve("index"))) {
            DocumentService service = service(index);

            DocumentInfo info = service.ingest("../../etc/leave policy.md", TestDocuments.bytes(LEAVE_POLICY));

            assertThat(info.fileName()).isEqualTo("leave_policy.md");
            assertThat(info.chunks()).isGreaterThan(1);
            assertThat(dataDir.resolve("documents").resolve(info.id()).resolve("leave_policy.md")).exists();
            assertThat(service.list()).containsExactly(info);

            assertThat(service.delete(info.id())).isTrue();
            assertThat(dataDir.resolve("documents").resolve(info.id())).doesNotExist();
            assertThat(service.list()).isEmpty();
        }
    }

    @Test
    void rebuildsIndexFromStoredOriginalsWhenIndexIsMissing() throws IOException {
        String id;
        try (ChunkIndex index = new ChunkIndex(dataDir.resolve("index"))) {
            id = service(index).ingest("leave-policy.md", TestDocuments.bytes(LEAVE_POLICY)).id();
        }
        FileSystemUtils.deleteRecursively(dataDir.resolve("index"));

        try (ChunkIndex index = new ChunkIndex(dataDir.resolve("index"))) {
            DocumentService service = service(index);
            assertThat(service.list()).isEmpty();

            service.rebuildIndexIfMissing();

            assertThat(service.list()).extracting(DocumentInfo::id).containsExactly(id);
            assertThat(index.search("sabbatical notice", 1)).isNotEmpty();
        }
    }

    @Test
    void rejectsUnsafeOrMissingFileNames() {
        assertThatThrownBy(() -> DocumentService.sanitize("..")).isInstanceOf(InvalidDocumentException.class);
        assertThatThrownBy(() -> DocumentService.sanitize(" ")).isInstanceOf(InvalidDocumentException.class);
        assertThat(DocumentService.sanitize("C:\\Users\\me\\notes.txt")).isEqualTo("notes.txt");
    }

    private DocumentService service(ChunkIndex index) {
        DocMindProperties properties = new DocMindProperties(dataDir,
                new DocMindProperties.Chunking(500, 100),
                new DocMindProperties.Retrieval(4, 8),
                new DocMindProperties.Anthropic("", "m", 1024, "low", Duration.ofSeconds(5), 0));
        return new DocumentService(extractor, chunker, index, properties);
    }

}
