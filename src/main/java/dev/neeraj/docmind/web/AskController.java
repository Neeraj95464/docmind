package dev.neeraj.docmind.web;

import dev.neeraj.docmind.ask.AskModels.AskRequest;
import dev.neeraj.docmind.ask.AskModels.AskResponse;
import dev.neeraj.docmind.ask.AskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Ask", description = "Grounded question answering over the indexed documents")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @Operation(summary = "Ask a question; returns an answer with numbered citations, or passages in retrieval-only mode")
    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request.question(), request.topK());
    }

    @Operation(summary = "Whether answer generation is enabled (an API key is configured)")
    @GetMapping("/status")
    public Status status() {
        return new Status(askService.generationEnabled());
    }

    public record Status(boolean generationEnabled) {
    }
}
