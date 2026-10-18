package dev.neeraj.docmind.ingest;

import dev.neeraj.docmind.config.DocMindProperties;
import dev.neeraj.docmind.index.ChunkIndex;
import dev.neeraj.docmind.index.DocumentInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

/**
 * Ingestion pipeline: extract text, chunk it, keep the original file and index the chunks.
 *
 * <p>Originals live under {@code <dataDir>/documents/<id>/<fileName>} so the Lucene index can be
 * rebuilt from them at startup if it is missing or was wiped.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final TextExtractor extractor;
    private final Chunker chunker;
    private final ChunkIndex index;
    private final Path documentsDir;

    public DocumentService(TextExtractor extractor, Chunker chunker, ChunkIndex index, DocMindProperties properties) {
        this.extractor = extractor;
        this.chunker = chunker;
        this.index = index;
        this.documentsDir = properties.dataDir().resolve("documents");
    }

    public DocumentInfo ingest(String originalFileName, byte[] content) {
        String fileName = sanitize(originalFileName);
        String text = extractor.extract(fileName, content);
        String id = UUID.randomUUID().toString();
        store(id, fileName, content);
        return index(id, fileName, text, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public List<DocumentInfo> list() {
        return index.list();
    }

    public Optional<DocumentInfo> find(String id) {
        return index.find(id);
    }

    public boolean delete(String id) {
        boolean deleted = index.delete(id);
        if (deleted) {
            try {
                FileSystemUtils.deleteRecursively(documentsDir.resolve(id));
            } catch (IOException e) {
                log.warn("Removed {} from the index but could not delete its stored file", id, e);
            }
        }
        return deleted;
    }

    /** Re-indexes stored originals when the index is empty, e.g. after the index directory was deleted. */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildIndexIfMissing() {
        if (!index.isEmpty() || !Files.isDirectory(documentsDir)) {
            return;
        }
        try (Stream<Path> dirs = Files.list(documentsDir)) {
            List<Path> documentDirs = dirs.filter(Files::isDirectory).sorted(Comparator.naturalOrder()).toList();
            for (Path dir : documentDirs) {
                reindex(dir);
            }
            if (!documentDirs.isEmpty()) {
                log.info("Rebuilt search index from {} stored document(s)", documentDirs.size());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rebuild index from " + documentsDir, e);
        }
    }

    private void reindex(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            Optional<Path> file = files.filter(Files::isRegularFile).findFirst();
            if (file.isEmpty()) {
                return;
            }
            String fileName = file.get().getFileName().toString();
            String text = extractor.extract(fileName, Files.readAllBytes(file.get()));
            index(dir.getFileName().toString(), fileName, text, Files.getLastModifiedTime(file.get()).toInstant());
        } catch (RuntimeException e) {
            log.warn("Skipping unreadable stored document in {}: {}", dir, e.getMessage());
        }
    }

    private DocumentInfo index(String id, String fileName, String text, Instant uploadedAt) {
        List<String> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new InvalidDocumentException("No indexable text found in '" + fileName + "'");
        }
        DocumentInfo info = new DocumentInfo(id, fileName, chunks.size(), text.length(), uploadedAt);
        index.add(info, chunks);
        log.info("Indexed '{}' as {} chunk(s)", fileName, chunks.size());
        return info;
    }

    private void store(String id, String fileName, byte[] content) {
        try {
            Path dir = Files.createDirectories(documentsDir.resolve(id));
            Files.write(dir.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + fileName, e);
        }
    }

    /** Strips any path components and unusual characters so the name is safe to use on disk. */
    static String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidDocumentException("File name is required");
        }
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.chars().allMatch(c -> c == '.')) {
            throw new InvalidDocumentException("Invalid file name: " + fileName);
        }
        return safe;
    }
}
