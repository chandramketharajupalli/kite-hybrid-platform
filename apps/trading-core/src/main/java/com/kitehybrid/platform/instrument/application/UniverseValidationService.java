package com.kitehybrid.platform.instrument.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only validation of local universe membership against the initialized registry. */
public final class UniverseValidationService {
    private final Path source;
    private final InstrumentRegistry registry;
    private final UniverseCsvImporter importer;

    public UniverseValidationService(Path source, InstrumentRegistry registry) {
        this(source, registry, new UniverseCsvImporter());
    }

    public UniverseValidationService(String configuredPath, InstrumentRegistry registry) {
        this(resolveSource(configuredPath), registry);
    }

    UniverseValidationService(Path source, InstrumentRegistry registry, UniverseCsvImporter importer) {
        this.source = Objects.requireNonNull(source).toAbsolutePath().normalize();
        this.registry = Objects.requireNonNull(registry);
        this.importer = Objects.requireNonNull(importer);
    }

    public UniverseCsvImporter.ImportResult validate() {
        if (registry.snapshot().version() < 1) {
            throw new IllegalStateException("Instrument registry is not initialized");
        }
        try (var reader = Files.newBufferedReader(source)) {
            return importer.importCsv(reader, registry);
        } catch (IOException failure) {
            throw new IllegalStateException("Universe CSV is unavailable", failure);
        }
    }

    public List<ReferenceEntry> lookup(String fragment) {
        String needle = normalizeFragment(fragment);
        var snapshot = registry.snapshot();
        if (snapshot.version() < 1) {
            throw new IllegalStateException("Instrument registry is not initialized");
        }
        return snapshot.byExchangeAndSymbol().values().stream()
                .filter(instrument -> instrument.tradingSymbol().contains(needle))
                .sorted(Comparator.comparing(com.kitehybrid.platform.instrument.domain.Instrument::exchange)
                        .thenComparing(com.kitehybrid.platform.instrument.domain.Instrument::tradingSymbol))
                .map(instrument -> new ReferenceEntry(instrument.exchange(), instrument.tradingSymbol(), instrument.segment()))
                .toList();
    }

    static Path resolveSource(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path found = null;
        for (int depth = 0; depth < 8 && current != null; depth++, current = current.getParent()) {
            Path candidate = current.resolve("universe.csv");
            if (Files.isRegularFile(candidate)) found = candidate;
        }
        return found != null ? found : Path.of("universe.csv").toAbsolutePath().normalize();
    }

    private static String normalizeFragment(String fragment) {
        if (fragment == null || fragment.isBlank() || fragment.length() > 32
                || fragment.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Symbol fragment must contain 1 to 32 printable characters");
        }
        return fragment.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record ReferenceEntry(String exchange, String symbol, String segment) {}
}
