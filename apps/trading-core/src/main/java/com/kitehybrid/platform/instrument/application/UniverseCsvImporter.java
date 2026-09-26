package com.kitehybrid.platform.instrument.application;

import com.kitehybrid.platform.instrument.domain.ExchangeSymbol;
import com.kitehybrid.platform.instrument.domain.Instrument;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Imports universe membership only. It does not refresh the registry, subscribe
 * to market data, activate strategies, or authorize execution.
 */
public final class UniverseCsvImporter {
    private static final String HEADER = "symbol,exchange,enabled";

    public ImportResult importCsv(Reader source, InstrumentRegistry registry) {
        Objects.requireNonNull(source, "Universe CSV source required");
        Objects.requireNonNull(registry, "Instrument registry required");
        var rows = read(source);
        var unique = new LinkedHashMap<ExchangeSymbol, Candidate>();
        int duplicates = 0;
        for (var row : rows) {
            var prior = unique.putIfAbsent(row.key(), row);
            if (prior != null) {
                if (prior.enabled() != row.enabled()) {
                    throw new IllegalArgumentException("Conflicting enabled configuration for " + row.key());
                }
                duplicates++;
            }
        }

        var entries = new ArrayList<ResolvedEntry>(unique.size());
        int resolved = 0;
        int enabled = 0;
        for (var candidate : unique.values()) {
            var instrument = registry.findByExchangeAndSymbol(candidate.key().exchange(), candidate.key().tradingSymbol());
            if (instrument.isPresent()) resolved++;
            if (candidate.enabled()) enabled++;
            entries.add(new ResolvedEntry(candidate.key(), candidate.enabled(), instrument));
        }
        return new ImportResult(rows.size(), entries.size(), duplicates, enabled, entries.size() - enabled,
                resolved, entries.size() - resolved, List.copyOf(entries));
    }

    private static List<Candidate> read(Reader source) {
        try {
            var reader = new BufferedReader(source);
            var header = reader.readLine();
            if (header != null && !header.isEmpty() && header.charAt(0) == '\uFEFF') header = header.substring(1);
            if (header == null || !HEADER.equals(String.join(",", parseLine(header)).toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Universe CSV header must be symbol,exchange,enabled");
            }
            var rows = new ArrayList<Candidate>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                var columns = parseLine(line);
                if (columns.size() != 3) throw new IllegalArgumentException("Invalid universe CSV row " + lineNumber);
                try {
                    var key = new ExchangeSymbol(columns.get(1), columns.get(0));
                    var enabled = parseBoolean(columns.get(2));
                    rows.add(new Candidate(key, enabled));
                } catch (RuntimeException invalid) {
                    throw new IllegalArgumentException("Invalid universe CSV row " + lineNumber, invalid);
                }
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("Universe CSV contains no data rows");
            return rows;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Unable to read universe CSV", failure);
        }
    }

    private static List<String> parseLine(String line) {
        var values = new ArrayList<String>();
        for (var value : line.split(",", -1)) {
            var trimmed = value.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
            }
            values.add(trimmed);
        }
        return values;
    }

    private static boolean parseBoolean(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "TRUE" -> true;
            case "FALSE" -> false;
            default -> throw new IllegalArgumentException("enabled must be TRUE or FALSE");
        };
    }

    private record Candidate(ExchangeSymbol key, boolean enabled) {}

    public record ResolvedEntry(ExchangeSymbol key, boolean enabled, Optional<Instrument> instrument) {
        public ResolvedEntry {
            Objects.requireNonNull(key);
            Objects.requireNonNull(instrument);
        }
    }

    public record ImportResult(int inputRows, int uniqueInstruments, int duplicatesRemoved,
                               int enabled, int disabled, int resolved, int unresolved, List<ResolvedEntry> entries) {
        public ImportResult {
            if (inputRows < 1 || uniqueInstruments < 1 || duplicatesRemoved < 0 || enabled < 0 || disabled < 0
                    || resolved < 0 || unresolved < 0 || uniqueInstruments != enabled + disabled
                    || uniqueInstruments != resolved + unresolved
                    || inputRows != uniqueInstruments + duplicatesRemoved) {
                throw new IllegalArgumentException("Inconsistent universe import counts");
            }
            entries = List.copyOf(Objects.requireNonNull(entries));
            if (entries.size() != uniqueInstruments) throw new IllegalArgumentException("Universe entries do not match count");
        }

        /** Membership entries are stored once; unresolved entries remain visible for operator correction. */
        public int stored() { return entries.size(); }
    }
}
