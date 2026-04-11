package com.zhj.learn.aihelper.service.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class AagConfig {

    @Value("${app.rag.docs-path:src/main/resources/docs}")
    private String docsPath;

    @Value("${app.rag.embedding-batch-size:10}")
    private int embeddingBatchSize;

    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Bean
    public ContentRetriever contentRetriever() {
        //读取文档
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(resolveDocsPath());
        //文档切割，设置最大值和覆盖值
        DocumentByParagraphSplitter documentByParagraphSplitter=
                new DocumentByParagraphSplitter(1000, 200);
        // 先切分文档，再按批次嵌入，避免 DashScope 单次请求超过 10 条
        List<TextSegment> segments = documentByParagraphSplitter.splitAll(documents).stream()
                .map(segment -> TextSegment.from(segment.metadata().getString("file_name")
                        + "\n" + segment.text(), segment.metadata()))
                .toList();
        int batchSize = Math.min(Math.max(embeddingBatchSize, 1), 10);
        for (int i = 0; i < segments.size(); i += batchSize) {
            List<TextSegment> batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
            List<Embedding> embeddings = qwenEmbeddingModel.embedAll(batch).content();
            embeddingStore.addAll(embeddings, batch);
        }

        //创建内容检索器
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(qwenEmbeddingModel)
                .maxResults(3)
                .minScore(0.7)
                .build();

        return contentRetriever;
    }

    private Path resolveDocsPath() {
        Path fileSystemPath = Paths.get(docsPath).toAbsolutePath().normalize();
        if (Files.isDirectory(fileSystemPath)) {
            return fileSystemPath;
        }

        try {
            return ResourceUtils.getFile("classpath:docs").toPath();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Docs directory not found: " + fileSystemPath, e);
        }
    }

}
