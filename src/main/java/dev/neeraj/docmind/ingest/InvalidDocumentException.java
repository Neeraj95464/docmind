package dev.neeraj.docmind.ingest;

/** The file type is supported but the content is empty or unreadable. */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
