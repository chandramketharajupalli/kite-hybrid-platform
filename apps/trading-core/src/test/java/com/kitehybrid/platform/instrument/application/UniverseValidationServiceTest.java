package com.kitehybrid.platform.instrument.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UniverseValidationServiceTest {
    @Test
    void blankConfiguredPathFindsRepositoryRootUniverse() {
        Path resolved = UniverseValidationService.resolveSource("");
        assertEquals("universe.csv", resolved.getFileName().toString());
        assertEquals("kite-hybrid-platform", resolved.getParent().getFileName().toString());
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.isRegularFile(resolved));
    }

    @Test
    void explicitConfiguredPathIsPreserved() {
        Path resolved = UniverseValidationService.resolveSource("custom/universe.csv");
        assertEquals(Path.of("custom", "universe.csv").toAbsolutePath().normalize(), resolved);
    }
}
