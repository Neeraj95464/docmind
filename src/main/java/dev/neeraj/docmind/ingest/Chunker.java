package dev.neeraj.docmind.ingest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits text into overlapping chunks that respect sentence boundaries where possible.
 *
 * <p>Text is first broken into segments (sentences and lines). Segments are packed greedily into
 * chunks of at most {@code size} characters; each new chunk starts with the trailing segments of
 * the previous one, up to {@code overlap} characters, so facts that straddle a boundary remain
 * retrievable. A single segment longer than {@code size} is hard-split with the same overlap.
 */
public final class Chunker {

    private static final Pattern SEGMENT_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+|\\R+");
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");

    private final int size;
    private final int overlap;

    public Chunker(int size, int overlap) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (overlap < 0 || overlap >= size) {
            throw new IllegalArgumentException("overlap must be >= 0 and < size");
        }
        this.size = size;
        this.overlap = overlap;
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        Deque<String> current = new ArrayDeque<>();
        int currentLength = 0;
        boolean hasNewContent = false; // false while `current` only holds overlap carried from the last chunk

        for (String segment : segments(text)) {
            if (segment.length() > size) {
                if (hasNewContent) {
                    flush(current, chunks);
                }
                hasNewContent = false;
                current.clear();
                currentLength = 0;
                hardSplit(segment, chunks);
                continue;
            }
            int added = currentLength == 0 ? segment.length() : segment.length() + 1;
            if (currentLength + added > size) {
                flush(current, chunks);
                currentLength = carryOverlap(current, segment.length());
                added = currentLength == 0 ? segment.length() : segment.length() + 1;
            }
            current.addLast(segment);
            currentLength += added;
            hasNewContent = true;
        }
        if (hasNewContent) {
            flush(current, chunks);
        }
        return chunks;
    }

    private List<String> segments(String text) {
        List<String> segments = new ArrayList<>();
        for (String raw : SEGMENT_BOUNDARY.split(text)) {
            String segment = WHITESPACE.matcher(raw).replaceAll(" ").strip();
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    /** Keeps trailing segments (up to the overlap budget) that still leave room for the next one. */
    private int carryOverlap(Deque<String> current, int nextSegmentLength) {
        Deque<String> kept = new ArrayDeque<>();
        int keptLength = 0;
        while (!current.isEmpty()) {
            String last = current.peekLast();
            int candidate = keptLength + last.length() + (kept.isEmpty() ? 0 : 1);
            if (candidate > overlap || candidate + 1 + nextSegmentLength > size) {
                break;
            }
            kept.addFirst(current.pollLast());
            keptLength = candidate;
        }
        current.clear();
        current.addAll(kept);
        return keptLength;
    }

    private void hardSplit(String segment, List<String> chunks) {
        int step = size - overlap;
        for (int start = 0; start < segment.length(); start += step) {
            int end = Math.min(start + size, segment.length());
            chunks.add(segment.substring(start, end).strip());
            if (end == segment.length()) {
                break;
            }
        }
    }

    private static void flush(Deque<String> current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(String.join(" ", current));
        }
    }
}
