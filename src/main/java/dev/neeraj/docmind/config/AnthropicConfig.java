package dev.neeraj.docmind.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import dev.neeraj.docmind.ask.AnswerGenerator;
import dev.neeraj.docmind.ask.ClaudeAnswerGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Wires the Claude-backed {@link AnswerGenerator} only when an API key is configured.
 * Without a key no generator bean exists and DocMind serves retrieval-only answers.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(AnthropicConfig.ApiKeyPresent.class)
public class AnthropicConfig {

    static final String API_KEY_PROPERTY = "docmind.anthropic.api-key";

    @Bean(destroyMethod = "close")
    AnthropicClient anthropicClient(DocMindProperties properties) {
        DocMindProperties.Anthropic cfg = properties.anthropic();
        return AnthropicOkHttpClient.builder()
                .apiKey(cfg.apiKey())
                .timeout(cfg.timeout())
                .maxRetries(cfg.maxRetries())
                .build();
    }

    @Bean
    AnswerGenerator claudeAnswerGenerator(AnthropicClient client, DocMindProperties properties) {
        return new ClaudeAnswerGenerator(client, properties.anthropic());
    }

    static class ApiKeyPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(API_KEY_PROPERTY));
        }
    }
}
