package com.lexoravisauls.client.modules.lyrics;

public record LyricLine(long timestampMs, String text) implements Comparable<LyricLine> {
    @Override
    public int compareTo(LyricLine o) {
        return Long.compare(this.timestampMs, o.timestampMs);
    }
}
