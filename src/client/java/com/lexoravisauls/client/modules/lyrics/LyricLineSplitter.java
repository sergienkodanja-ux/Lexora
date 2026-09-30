package com.lexoravisauls.client.modules.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LyricLineSplitter {
    private LyricLineSplitter() {}

    /**
     * Splits long lyric lines (> 3-4 words) into shorter rhythmic chunks with micro-timings.
     */
    public static List<LyricLine> splitLongLines(List<LyricLine> originalLines) {
        if (originalLines == null || originalLines.isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricLine> result = new ArrayList<>();

        for (int i = 0; i < originalLines.size(); i++) {
            LyricLine current = originalLines.get(i);
            long nextTimeMs = (i + 1 < originalLines.size()) ? originalLines.get(i + 1).timestampMs() : current.timestampMs() + 4000L;
            long lineDuration = Math.max(1500L, nextTimeMs - current.timestampMs());

            String text = current.text().trim();
            String[] words = text.split("\\s+");

            if (words.length <= 4) {
                result.add(current);
            } else {
                // Split into chunks of 2-3 words
                int wordsPerChunk = words.length > 7 ? 3 : 2;
                List<String> chunks = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                int count = 0;

                for (String word : words) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(word);
                    count++;
                    if (count >= wordsPerChunk) {
                        chunks.add(sb.toString());
                        sb.setLength(0);
                        count = 0;
                    }
                }
                if (sb.length() > 0) {
                    if (!chunks.isEmpty() && count <= 1) {
                        // Append trailing single word to last chunk to avoid orphan single words
                        chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + " " + sb.toString());
                    } else {
                        chunks.add(sb.toString());
                    }
                }

                int numChunks = chunks.size();
                long chunkDuration = lineDuration / numChunks;

                for (int c = 0; c < numChunks; c++) {
                    long chunkStartMs = current.timestampMs() + (c * chunkDuration);
                    result.add(new LyricLine(chunkStartMs, chunks.get(c)));
                }
            }
        }

        Collections.sort(result);
        return result;
    }
}
