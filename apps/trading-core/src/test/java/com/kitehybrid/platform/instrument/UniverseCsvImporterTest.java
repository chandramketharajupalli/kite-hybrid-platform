package com.kitehybrid.platform.instrument;

import com.kitehybrid.platform.instrument.application.UniverseCsvImporter;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UniverseCsvImporterTest {
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");

    @Test void repositoryUniverseContainsOnlyUniqueNormalizedEntries() throws Exception {
        try (var source = java.nio.file.Files.newBufferedReader(universePath())) {
            var result = new UniverseCsvImporter().importCsv(source, new InMemoryInstrumentRegistry());
            assertEquals(1111, result.inputRows());
            assertEquals(1111, result.uniqueInstruments());
            assertEquals(0, result.duplicatesRemoved());
            assertEquals(1111, result.enabled());
            assertEquals(0, result.disabled());
            assertEquals(0, result.resolved());
            assertEquals(1111, result.unresolved());
            assertEquals(1111, result.stored());
            assertEquals(1111, result.entries().stream().map(UniverseCsvImporter.ResolvedEntry::key).distinct().count());
        }
    }

    private static java.nio.file.Path universePath() {
        var current = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        java.nio.file.Path found = null;
        for (int i = 0; i < 4 && current != null; i++, current = current.getParent()) {
            var candidate = current.resolve("universe.csv");
            if (java.nio.file.Files.isRegularFile(candidate)) found = candidate;
        }
        if (found != null) return found;
        throw new IllegalStateException("universe.csv not found from " + System.getProperty("user.dir"));
    }

    @Test void normalizationHappensBeforeDeduplicationAndRegistryResolutionUsesNormalizedKey() {
        var registry = new InMemoryInstrumentRegistry();
        var instrument = InstrumentFixtures.cash("1", "ABC");
        registry.replace(List.of(instrument), NOW);
        var result = new UniverseCsvImporter().importCsv(new StringReader(
                "symbol,exchange,enabled\n abc , nse , TRUE\nABC,NSE,true\n"), registry);
        assertEquals(2, result.inputRows());
        assertEquals(1, result.uniqueInstruments());
        assertEquals(1, result.duplicatesRemoved());
        assertEquals(1, result.enabled());
        assertEquals(0, result.disabled());
        assertEquals(1, result.resolved());
        assertEquals("ABC", result.entries().getFirst().key().tradingSymbol());
        assertTrue(result.entries().getFirst().instrument().isPresent());
    }

    @Test void conflictingEnabledConfigurationFailsBeforeAnyResultIsPublished() {
        var registry = new InMemoryInstrumentRegistry();
        assertThrows(IllegalArgumentException.class, () -> new UniverseCsvImporter().importCsv(new StringReader(
                "symbol,exchange,enabled\nABC,NSE,TRUE\n abc , nse , FALSE\n"), registry));
    }
}
