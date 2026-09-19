package com.kitehybrid.platform.instrument.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * V1 identity uses length-prefixed UTF-8 canonical semantic fields, never broker tokens.
 * This algorithm is a persistent identity contract; changes require an explicit migration.
 */
final class InstrumentIdentity {
    private InstrumentIdentity() {}

    static InstrumentId derive(String exchange, String symbol, String segment, InstrumentType type,
                               Optional<LocalDate> expiry, Optional<BigDecimal> strike) {
        List<byte[]> fields = List.of("instrument-id:v1", exchange, symbol, segment, type.name(),
                        expiry.map(LocalDate::toString).orElse(""),
                        strike.map(value -> value.stripTrailingZeros().toPlainString()).orElse(""))
                .stream().map(value -> value.getBytes(StandardCharsets.UTF_8)).toList();
        int size = fields.stream().mapToInt(field -> Integer.BYTES + field.length).sum();
        ByteBuffer canonical = ByteBuffer.allocate(size);
        fields.forEach(field -> canonical.putInt(field.length).put(field));
        return new InstrumentId(UUID.nameUUIDFromBytes(canonical.array()));
    }
}
