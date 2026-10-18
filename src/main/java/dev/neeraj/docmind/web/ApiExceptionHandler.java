package dev.neeraj.docmind.web;

import dev.neeraj.docmind.ask.AnswerGenerationException;
import dev.neeraj.docmind.ingest.InvalidDocumentException;
import dev.neeraj.docmind.ingest.UnsupportedDocumentException;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain errors to RFC 9457 problem details. Framework errors (validation, malformed JSON,
 * missing parameters, oversized uploads -> 413) are handled by the
 * {@link ResponseEntityExceptionHandler} base class.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UnsupportedDocumentException.class)
    ProblemDetail unsupported(UnsupportedDocumentException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported document type", e.getMessage());
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ProblemDetail invalid(InvalidDocumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid document", e.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", e.getMessage());
    }

    @ExceptionHandler(AnswerGenerationException.class)
    ProblemDetail generationFailed(AnswerGenerationException e) {
        log.warn("Answer generation failed: {}", e.getMessage(), e.getCause());
        return problem(HttpStatus.BAD_GATEWAY, "Answer generation failed", e.getMessage());
    }

    /** Adds per-field messages, e.g. {@code "errors": {"question": "must not be blank"}}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = ex.getBody();
        body.setDetail("Request validation failed");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
