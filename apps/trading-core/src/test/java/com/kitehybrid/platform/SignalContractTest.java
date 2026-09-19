package com.kitehybrid.platform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kitehybrid.platform.order.infrastructure.SignalEvent;
import com.networknt.schema.*;
import java.math.BigDecimal;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignalContractTest {
    private final Path contracts = Path.of(System.getProperty("contracts.dir"));
    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();

    private JsonSchema schema() {
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder ->
                builder.schemaMappers(mappers -> mappers.mapPrefix(
                        "https://kite-hybrid.local/contracts/v1/",
                        contracts.resolve("schemas/v1").toUri().toString())));
        return factory.getSchema(SchemaLocation.of("https://kite-hybrid.local/contracts/v1/SignalEvent.v1.schema.json"),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
    }

    @Test void sharedFixtureRoundTripsWithoutLosingDecimalPrecision() throws Exception {
        var original = mapper.readTree(contracts.resolve("fixtures/v1/signal.valid.json").toFile());
        assertTrue(schema().validate(original).isEmpty());
        var event = mapper.treeToValue(original, SignalEvent.class);
        assertEquals(new BigDecimal("123.4500"), event.toDomain().referencePrice());
        var encoded = mapper.valueToTree(event);
        assertTrue(schema().validate(encoded).isEmpty());
        assertEquals(original, encoded);
    }

    @Test void sharedInvalidFixturesAreRejectedBeforeBinding() throws Exception {
        try (var paths = Files.list(contracts.resolve("fixtures/v1"))) {
            var invalid = paths.filter(p -> p.getFileName().toString().endsWith(".invalid.json")).toList();
            assertFalse(invalid.isEmpty());
            for (var path : invalid) {
                assertFalse(schema().validate(mapper.readTree(path.toFile())).isEmpty(), path.toString());
            }
        }
    }

    @Test void bindingRejectsFloatingTokenEvenWhenSchemaSeesAnIntegralValue() throws Exception {
        var raw = Files.readString(contracts.resolve("fixtures/v1/signal.valid.json"))
                .replace("\"quantity\": 10", "\"quantity\": 10.0");
        // JSON Schema defines integer mathematically; binding must also forbid coercion.
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.readValue(raw, SignalEvent.class));
    }
}
