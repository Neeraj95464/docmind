package dev.neeraj.docmind.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void readsPlainTextAndMarkdownAsUtf8() {
        assertThat(extractor.extract("notes.txt", "café".getBytes(StandardCharsets.UTF_8))).isEqualTo("café");
        assertThat(extractor.extract("README.MD", "# Title".getBytes(StandardCharsets.UTF_8))).isEqualTo("# Title");
    }

    @Test
    void extractsTextFromPdf() throws IOException {
        String text = extractor.extract("policy.pdf", pdfWith("Remote work is allowed two days per week."));
        assertThat(text).contains("Remote work is allowed two days per week.");
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThatThrownBy(() -> extractor.extract("slides.pptx", new byte[] {1}))
                .isInstanceOf(UnsupportedDocumentException.class);
        assertThatThrownBy(() -> extractor.extract("no-extension", new byte[] {1}))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void rejectsEmptyOrCorruptContent() {
        assertThatThrownBy(() -> extractor.extract("empty.txt", "  \n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDocumentException.class);
        assertThatThrownBy(() -> extractor.extract("broken.pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidDocumentException.class);
    }

    private static byte[] pdfWith(String line) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(line);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
