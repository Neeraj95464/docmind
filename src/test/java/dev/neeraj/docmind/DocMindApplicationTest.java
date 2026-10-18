package dev.neeraj.docmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.neeraj.docmind.ask.AnswerGenerator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end through the real Spring context, without an API key (retrieval-only mode). */
@SpringBootTest(properties = "docmind.anthropic.api-key=")
@AutoConfigureMockMvc
class DocMindApplicationTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("docmind.data-dir", () -> dataDir.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Test
    void uploadThenAskInRetrievalOnlyMode() throws Exception {
        assertThat(context.getBeanNamesForType(AnswerGenerator.class)).isEmpty();

        mvc.perform(multipart("/api/documents").file(new MockMultipartFile(
                        "file", "it-onboarding-guide.txt", "text/plain", TestDocuments.bytes(TestDocuments.IT_ONBOARDING))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Which second factor is accepted for MFA?\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("RETRIEVAL_ONLY"))
                .andExpect(jsonPath("$.citations[0].fileName").value("it-onboarding-guide.txt"))
                .andExpect(jsonPath("$.citations.length()").value(2));

        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/ask']").exists());
    }
}
