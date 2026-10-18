package dev.neeraj.docmind.ingest;

/** The uploaded file type is not one DocMind can ingest. */
public class UnsupportedDocumentException extends RuntimeException {

    public UnsupportedDocumentException(String message) {
        super(message);
    }
}
