package dev.neeraj.docmind.index;

import java.time.Instant;

/** Metadata for an ingested document. */
public record DocumentInfo(String id, String fileName, int chunks, long characters, Instant uploadedAt) {
}
