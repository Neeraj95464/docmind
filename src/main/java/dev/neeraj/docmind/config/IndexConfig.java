package dev.neeraj.docmind.config;

import dev.neeraj.docmind.index.ChunkIndex;
import dev.neeraj.docmind.ingest.Chunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IndexConfig {

    @Bean(destroyMethod = "close")
    ChunkIndex chunkIndex(DocMindProperties properties) {
        return new ChunkIndex(properties.dataDir().resolve("index"));
    }

    @Bean
    Chunker chunker(DocMindProperties properties) {
        return new Chunker(properties.chunking().size(), properties.chunking().overlap());
    }
}
