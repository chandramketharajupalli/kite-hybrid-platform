package com.kitehybrid.platform.historical.domain;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class HistoricalFingerprint {
    private HistoricalFingerprint() { }
    public static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static String bars(List<HistoricalBar> bars) {
        var text=new StringBuilder("historical-bars-v1\n");
        for(var b:bars) text.append(b.instrumentId().value()).append('|').append(b.interval()).append('|')
                .append(b.startTime()).append('|').append(b.open().toPlainString()).append('|')
                .append(b.high().toPlainString()).append('|').append(b.low().toPlainString()).append('|')
                .append(b.close().toPlainString()).append('|').append(b.volume()).append('|')
                .append(b.openInterest().map(java.math.BigDecimal::toPlainString).orElse("ABSENT")).append('\n');
        return sha256(text.toString());
    }
}
