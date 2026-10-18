package dev.neeraj.docmind.ask;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import dev.neeraj.docmind.config.DocMindProperties;
import dev.neeraj.docmind.index.SearchHit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link AnswerGenerator} backed by the Claude Messages API via the official Anthropic Java SDK. */
public class ClaudeAnswerGenerator implements AnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAnswerGenerator.class);

    private final AnthropicClient client;
    private final DocMindProperties.Anthropic config;

    public ClaudeAnswerGenerator(AnthropicClient client, DocMindProperties.Anthropic config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public GeneratedAnswer generate(String question, List<SearchHit> passages) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .system(PromptBuilder.SYSTEM_PROMPT)
                // Low effort keeps latency and token spend down; grounded Q&A rarely needs deep reasoning.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(config.effort())).build())
                .addUserMessage(PromptBuilder.userMessage(question, passages))
                .build();

        Message response = call(params);
        Optional<StopReason> stopReason = response.stopReason();
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining())
                .strip();

        if (stopReason.filter(StopReason.REFUSAL::equals).isPresent()) {
            text = "The model declined to answer this question.";
        } else if (stopReason.filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            text = text + "\n\n(Answer truncated: increase docmind.anthropic.max-tokens for longer answers.)";
        }
        long in = response.usage().inputTokens();
        long out = response.usage().outputTokens();
        log.debug("Claude answered with {} input / {} output tokens", in, out);
        return new GeneratedAnswer(text, in, out);
    }

    @Override
    public String modelName() {
        return config.model();
    }

    private Message call(MessageCreateParams params) {
        try {
            return client.messages().create(params);
        } catch (UnauthorizedException e) {
            throw new AnswerGenerationException("Claude rejected the API key - check ANTHROPIC_API_KEY", e);
        } catch (RateLimitException e) {
            throw new AnswerGenerationException("Claude rate limit reached - retry shortly", e);
        } catch (AnthropicServiceException e) {
            throw new AnswerGenerationException("Claude API error (HTTP " + e.statusCode() + ")", e);
        } catch (AnthropicIoException e) {
            throw new AnswerGenerationException("Could not reach the Claude API: " + e.getMessage(), e);
        }
    }
}
