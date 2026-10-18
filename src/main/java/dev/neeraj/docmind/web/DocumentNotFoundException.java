package dev.neeraj.docmind.web;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String id) {
        super("No document with id " + id);
    }
}
