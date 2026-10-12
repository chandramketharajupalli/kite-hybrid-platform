package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.csv.*;
import com.kitehybrid.platform.instrument.application.DatedInstrumentMaster;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;

/** Bytes supplied by the caller only. No session, transport, file loader or network capability. */
public final class KiteOfflineInstrumentMaster {
    public DatedInstrumentMaster importBytes(byte[] supplied, DatedInstrumentMaster.Provenance provenance) {
        Objects.requireNonNull(supplied); Objects.requireNonNull(provenance);
        if (supplied.length != provenance.bytes() || supplied.length > 32 * 1024 * 1024)
            throw new IllegalArgumentException("MASTER_SIZE_MISMATCH");
        byte[] bytes = supplied.clone();
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(provenance.sha256())) throw new IllegalArgumentException("MASTER_HASH_MISMATCH");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            // Deliberately reject BOM/mixed encodings; never silently transcode evidence.
            if (text.indexOf('\ufeff') >= 0 || text.indexOf('\u0000') >= 0)
                throw new IllegalArgumentException("MASTER_ENCODING_UNSUPPORTED");
            var instruments = new KiteInstrumentCsvMapper().map(text);
            var csv = CsvMapper.builder().enable(CsvParser.Feature.FAIL_ON_MISSING_COLUMNS).build();
            var result = new ArrayList<DatedInstrumentMaster.Candidate>();
            try (var rows = csv.readerFor(new TypeReference<Map<String, String>>() {})
                    .with(CsvSchema.emptySchema().withHeader()).<Map<String, String>>readValues(text)) {
                int index = 0;
                while (rows.hasNextValue()) {
                    var row = rows.nextValue();
                    String listing = row.getOrDefault("listing_status", "").strip();
                    var state = listing.isEmpty() ? DatedInstrumentMaster.Listing.UNSPECIFIED
                            : DatedInstrumentMaster.Listing.valueOf(listing);
                    result.add(new DatedInstrumentMaster.Candidate(index + 1, instruments.get(index), state));
                    index++;
                }
                if (index != instruments.size()) throw new IllegalArgumentException("MASTER_ROW_MISMATCH");
            }
            return new DatedInstrumentMaster(provenance, result);
        } catch (Exception invalid) {
            // Do not expose input records, response bodies or caller-supplied values in errors.
            throw new IllegalArgumentException("INVALID_MASTER_SOURCE");
        }
    }
}
