package dev.neeraj.docmind.index;

/** A chunk returned by BM25 retrieval, with its position in the source document. */
public record SearchHit(String documentId, String fileName, int chunkIndex, String text, float score) {
}
