package dev.neeraj.docmind.index;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

/**
 * In-process Lucene index of document chunks, ranked with BM25.
 *
 * <p>Each chunk is one Lucene document. The first chunk of every source document additionally
 * carries the document-level metadata, which keeps listing cheap without a second store.
 */
public class ChunkIndex implements Closeable {

    private static final String DOC_ID = "docId";
    private static final String FILE_NAME = "fileName";
    private static final String CHUNK_INDEX = "chunkIndex";
    private static final String TEXT = "text";
    private static final String HEAD = "head";
    private static final String CHUNK_COUNT = "chunkCount";
    private static final String CHARACTERS = "characters";
    private static final String UPLOADED_AT = "uploadedAt";

    private final FSDirectory directory;
    private final Analyzer analyzer = new EnglishAnalyzer();
    private final IndexWriter writer;
    private final SearcherManager searcherManager;

    public ChunkIndex(Path indexDir) {
        try {
            Files.createDirectories(indexDir);
            this.directory = FSDirectory.open(indexDir);
            IndexWriterConfig config = new IndexWriterConfig(analyzer)
                    .setSimilarity(new BM25Similarity())
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.writer = new IndexWriter(directory, config);
            this.writer.commit(); // make sure an (empty) index exists before the first search
            this.searcherManager = new SearcherManager(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open Lucene index at " + indexDir, e);
        }
    }

    public synchronized void add(DocumentInfo info, List<String> chunks) {
        try {
            List<Document> docs = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Document doc = new Document();
                doc.add(new StringField(DOC_ID, info.id(), Field.Store.YES));
                doc.add(new StoredField(FILE_NAME, info.fileName()));
                doc.add(new StoredField(CHUNK_INDEX, i));
                doc.add(new TextField(TEXT, chunks.get(i), Field.Store.YES));
                if (i == 0) {
                    doc.add(new StringField(HEAD, "true", Field.Store.NO));
                    doc.add(new StoredField(CHUNK_COUNT, chunks.size()));
                    doc.add(new StoredField(CHARACTERS, info.characters()));
                    doc.add(new StoredField(UPLOADED_AT, info.uploadedAt().toEpochMilli()));
                }
                docs.add(doc);
            }
            writer.updateDocuments(new Term(DOC_ID, info.id()), docs);
            commit();
        } catch (IOException e) {
            throw new UncheckedIOException("Indexing failed for " + info.fileName(), e);
        }
    }

    public synchronized boolean delete(String documentId) {
        if (find(documentId).isEmpty()) {
            return false;
        }
        try {
            writer.deleteDocuments(new Term(DOC_ID, documentId));
            commit();
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Delete failed for " + documentId, e);
        }
    }

    public List<SearchHit> search(String queryText, int topK) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        Optional<Query> parsed = parse(queryText);
        if (parsed.isEmpty()) {
            return List.of();
        }
        Query query = parsed.get();
        return withSearcher(searcher -> {
            List<SearchHit> hits = new ArrayList<>();
            StoredFields stored = searcher.storedFields();
            for (ScoreDoc scoreDoc : searcher.search(query, topK).scoreDocs) {
                Document doc = stored.document(scoreDoc.doc);
                hits.add(new SearchHit(
                        doc.get(DOC_ID),
                        doc.get(FILE_NAME),
                        doc.getField(CHUNK_INDEX).numericValue().intValue(),
                        doc.get(TEXT),
                        scoreDoc.score));
            }
            return hits;
        });
    }

    public List<DocumentInfo> list() {
        return headDocuments(new TermQuery(new Term(HEAD, "true"))).stream()
                .sorted(Comparator.comparing(DocumentInfo::uploadedAt).reversed())
                .toList();
    }

    public Optional<DocumentInfo> find(String documentId) {
        return headDocuments(new TermQuery(new Term(DOC_ID, documentId))).stream().findFirst();
    }

    public boolean isEmpty() {
        return withSearcher(searcher -> searcher.getIndexReader().numDocs() == 0);
    }

    private List<DocumentInfo> headDocuments(Query query) {
        return withSearcher(searcher -> {
            List<DocumentInfo> result = new ArrayList<>();
            StoredFields stored = searcher.storedFields();
            for (ScoreDoc scoreDoc : searcher.search(query, Math.max(1, searcher.getIndexReader().maxDoc())).scoreDocs) {
                Document doc = stored.document(scoreDoc.doc);
                if (doc.getField(CHUNK_COUNT) == null) {
                    continue;
                }
                result.add(new DocumentInfo(
                        doc.get(DOC_ID),
                        doc.get(FILE_NAME),
                        doc.getField(CHUNK_COUNT).numericValue().intValue(),
                        doc.getField(CHARACTERS).numericValue().longValue(),
                        Instant.ofEpochMilli(doc.getField(UPLOADED_AT).numericValue().longValue())));
            }
            return result;
        });
    }

    /** Free text is escaped, so user input can never inject Lucene query syntax. */
    private Optional<Query> parse(String queryText) {
        QueryParser parser = new QueryParser(TEXT, analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);
        try {
            return Optional.of(parser.parse(QueryParser.escape(queryText)));
        } catch (ParseException e) {
            return Optional.empty(); // e.g. the question consisted only of stop words or punctuation
        }
    }

    private void commit() throws IOException {
        writer.commit();
        searcherManager.maybeRefreshBlocking();
    }

    private <T> T withSearcher(SearcherFunction<T> fn) {
        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                searcher.setSimilarity(new BM25Similarity());
                return fn.apply(searcher);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Index read failed", e);
        }
    }

    @Override
    public void close() throws IOException {
        searcherManager.close();
        writer.close();
        directory.close();
        analyzer.close();
    }

    @FunctionalInterface
    private interface SearcherFunction<T> {
        T apply(IndexSearcher searcher) throws IOException;
    }
}
