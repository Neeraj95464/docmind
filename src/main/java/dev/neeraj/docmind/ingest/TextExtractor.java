package dev.neeraj.docmind.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts plain text from the supported upload formats: .txt, .md and .pdf. */
@Component
public class TextExtractor {

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "markdown", "pdf");

    public String extract(String fileName, byte[] content) {
        String extension = extensionOf(fileName)
                .filter(SUPPORTED_EXTENSIONS::contains)
                .orElseThrow(() -> new UnsupportedDocumentException(
                        "Unsupported file type: '" + fileName + "'. Supported: " + SUPPORTED_EXTENSIONS));
        String text = "pdf".equals(extension) ? extractPdf(content) : new String(content, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new InvalidDocumentException("No extractable text found in '" + fileName + "'");
        }
        return text;
    }

    public static boolean isSupported(String fileName) {
        return extensionOf(fileName).filter(SUPPORTED_EXTENSIONS::contains).isPresent();
    }

    private static Optional<String> extensionOf(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? Optional.empty() : Optional.of(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String extractPdf(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(pdf);
        } catch (IOException e) {
            throw new InvalidDocumentException("Could not read PDF: " + e.getMessage());
        }
    }
}
