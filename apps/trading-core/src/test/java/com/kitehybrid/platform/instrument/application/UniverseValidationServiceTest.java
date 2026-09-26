package com.kitehybrid.platform.instrument.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UniverseValidationServiceTest {
    @Test
    void unrelatedAncestorFileCannotOverrideCanonicalRepository(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var repository = directory.resolve("repository");
        var module = repository.resolve("apps/trading-core");
        java.nio.file.Files.createDirectories(module);
        java.nio.file.Files.writeString(repository.resolve("mvnw"), "wrapper marker");
        java.nio.file.Files.writeString(repository.resolve("pom.xml"), "project marker");
        java.nio.file.Files.writeString(repository.resolve("universe.csv"), "canonical");
        java.nio.file.Files.writeString(directory.resolve("universe.csv"), "unrelated");
        var previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", module.toString());
            assertEquals(repository.resolve("universe.csv"), UniverseValidationService.resolveSource(""));
        } finally { System.setProperty("user.dir", previous); }
    }
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
