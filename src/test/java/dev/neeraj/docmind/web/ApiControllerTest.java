package dev.neeraj.docmind.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.neeraj.docmind.ask.AnswerGenerationException;
import dev.neeraj.docmind.ask.AskModels.AskResponse;
import dev.neeraj.docmind.ask.AskModels.Citation;
import dev.neeraj.docmind.ask.AskModels.Mode;
import dev.neeraj.docmind.ask.AskModels.Usage;
import dev.neeraj.docmind.ask.AskService;
import dev.neeraj.docmind.index.DocumentInfo;
import dev.neeraj.docmind.ingest.DocumentService;
import dev.neeraj.docmind.ingest.UnsupportedDocumentException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({DocumentController.class, AskController.class})
class ApiControllerTest {

    private static final DocumentInfo DOC =
            new DocumentInfo("doc-1", "leave-policy.md", 4, 2900, Instant.parse("2026-01-01T00:00:00Z"));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DocumentService documentService;

    @MockitoBean
    AskService askService;

    @Test
    void uploadReturnsCreatedDocument() throws Exception {
        given(documentService.ingest(eq("leave-policy.md"), any())).willReturn(DOC);

        mvc.perform(multipart("/api/documents").file(markdown("leave-policy.md", "# Leave")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/documents/doc-1"))
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.chunks").value(4));
    }

    @Test
    void uploadOfUnsupportedTypeIsAProblemDetail() throws Exception {
        given(documentService.ingest(anyString(), any()))
                .willThrow(new UnsupportedDocumentException("Unsupported file type: 'a.exe'"));

        mvc.perform(multipart("/api/documents").file(markdown("a.exe", "MZ")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Unsupported document type"))
                .andExpect(jsonPath("$.detail").value("Unsupported file type: 'a.exe'"));
    }

    @Test
    void emptyUploadIsRejected() throws Exception {
        mvc.perform(multipart("/api/documents").file(markdown("empty.md", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid document"));
        verifyNoInteractions(documentService);
    }

    @Test
    void missingFilePartIsABadRequest() throws Exception {
        mvc.perform(multipart("/api/documents"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listsDocuments() throws Exception {
        given(documentService.list()).willReturn(List.of(DOC));

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("leave-policy.md"));
    }

    @Test
    void deleteReturnsNoContentOrNotFound() throws Exception {
        given(documentService.delete("doc-1")).willReturn(true);

        mvc.perform(delete("/api/documents/doc-1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/documents/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Document not found"));
    }

    @Test
    void askReturnsAnswerWithCitations() throws Exception {
        given(askService.ask("How much leave?", 2)).willReturn(new AskResponse(
                "24 days [1].", Mode.GENERATED, "claude-sonnet-5-5",
                List.of(new Citation(1, "doc-1", "leave-policy.md", 0, "24 days of paid annual leave", 3.2f, true)),
                new Usage(500, 40)));

        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How much leave?\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("GENERATED"))
                .andExpect(jsonPath("$.citations[0].fileName").value("leave-policy.md"))
                .andExpect(jsonPath("$.citations[0].cited").value(true))
                .andExpect(jsonPath("$.usage.outputTokens").value(40));
    }

    @Test
    void askValidatesInput() throws Exception {
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errors.question").value("must not be blank"));
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"ok\",\"topK\":50}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(askService);
    }

    @Test
    void upstreamFailureIsABadGateway() throws Exception {
        given(askService.ask(anyString(), any()))
                .willThrow(new AnswerGenerationException("Claude rate limit reached - retry shortly", null));

        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Claude rate limit reached - retry shortly"));
    }

    @Test
    void statusReportsGenerationMode() throws Exception {
        given(askService.generationEnabled()).willReturn(false);

        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationEnabled").value(false));
    }

    private static MockMultipartFile markdown(String name, String content) {
        return new MockMultipartFile("file", name, "text/markdown", content.getBytes());
    }
}
