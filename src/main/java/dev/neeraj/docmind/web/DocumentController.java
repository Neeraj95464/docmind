package dev.neeraj.docmind.web;

import dev.neeraj.docmind.index.DocumentInfo;
import dev.neeraj.docmind.ingest.DocumentService;
import dev.neeraj.docmind.ingest.InvalidDocumentException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Upload, list and remove indexed documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @Operation(summary = "Upload a .txt, .md or .pdf document; it is chunked and indexed immediately")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInfo> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDocumentException("Uploaded file is empty");
        }
        DocumentInfo info = documents.ingest(file.getOriginalFilename(), bytes(file));
        return ResponseEntity.created(URI.create("/api/documents/" + info.id())).body(info);
    }

    @Operation(summary = "List indexed documents, newest first")
    @GetMapping
    public List<DocumentInfo> list() {
        return documents.list();
    }

    @Operation(summary = "Remove a document and its chunks from the index")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!documents.delete(id)) {
            throw new DocumentNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }
}
