package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.csv.*;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.domain.*;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.INVALID_RESPONSE;

/** Broker CSV interpretation only. Candidate-wide consistency belongs to the registry. */
final class KiteInstrumentCsvMapper {
    private static final Set<String> REQUIRED = Set.of("instrument_token", "tradingsymbol", "exchange",
            "segment", "instrument_type", "expiry", "strike", "tick_size", "lot_size");
    private final CsvMapper csv = CsvMapper.builder()
            .enable(CsvParser.Feature.FAIL_ON_MISSING_COLUMNS, CsvParser.Feature.TRIM_SPACES)
            .disable(CsvParser.Feature.IGNORE_TRAILING_UNMAPPABLE,
                    CsvParser.Feature.ALLOW_TRAILING_COMMA, CsvParser.Feature.SKIP_EMPTY_LINES).build();

    List<Instrument> map(String body) {
        try (var rows = csv.readerFor(new TypeReference<Map<String, String>>() {})
                .with(CsvSchema.emptySchema().withHeader()).<Map<String, String>>readValues(new StringReader(body))) {
            List<String> headers = ((CsvSchema) rows.getParserSchema()).getColumnNames();
            if (headers.stream().anyMatch(h -> h == null || h.isBlank())
                    || new HashSet<>(headers).size() != headers.size() || !headers.containsAll(REQUIRED))
                throw new BrokerReadException(INVALID_RESPONSE);
            var instruments = new ArrayList<Instrument>();
            while (rows.hasNextValue()) instruments.add(mapRecord(rows.nextValue()));
            if (instruments.isEmpty()) throw new BrokerReadException(INVALID_RESPONSE);
            return List.copyOf(instruments);
        } catch (BrokerReadException safe) { throw safe; }
        catch (IOException | RuntimeException invalid) { throw new BrokerReadException(INVALID_RESPONSE); }
    }

    Instrument mapRecord(Map<String, String> row) {
        try {
            String exchange = required(row, "exchange").toUpperCase(Locale.ROOT);
            String sourceSegment = required(row, "segment").toUpperCase(Locale.ROOT);
            String sourceType = required(row, "instrument_type").toUpperCase(Locale.ROOT);
            InstrumentType type;
            String segment;
            if (sourceType.equals("EQ") && sourceSegment.equals("INDICES")) {
                type = InstrumentType.INDEX; segment = "INDICES";
            } else if (sourceType.equals("EQ") && sourceSegment.equals(exchange)) {
                type = InstrumentType.CASH; segment = "CASH";
            } else if (sourceType.equals("FUT") && (sourceSegment.equals(exchange + "-FUT")
                    || (exchange.equals("MCX") && sourceSegment.equals("MCX")))) {
                type = InstrumentType.FUTURE; segment = "FUTURES";
            } else if ((sourceType.equals("CE") || sourceType.equals("PE"))
                    && sourceSegment.equals(exchange + "-OPT")) {
                type = sourceType.equals("CE") ? InstrumentType.CALL_OPTION : InstrumentType.PUT_OPTION;
                segment = "OPTIONS";
            } else throw new IllegalArgumentException("Unsupported instrument classification");

            String tokenText = required(row, "instrument_token");
            if (!tokenText.matches("[0-9]{1,10}")) throw new IllegalArgumentException("Invalid token");
            long token = Long.parseLong(tokenText);
            if (token < 1 || token > 0xffff_ffffL) throw new IllegalArgumentException("Invalid token");
            String lotText = required(row, "lot_size");
            if (!lotText.matches("[0-9]{1,10}")) throw new IllegalArgumentException("Invalid lot");
            int lot = Integer.parseInt(lotText);
            String expiryText = optionalText(row, "expiry");
            Optional<LocalDate> expiry = expiryText.isEmpty() ? Optional.empty()
                    : Optional.of(parseDate(expiryText));
            String strikeText = optionalText(row, "strike");
            boolean option = type == InstrumentType.CALL_OPTION || type == InstrumentType.PUT_OPTION;
            Optional<BigDecimal> strike = option ? Optional.of(decimal(strikeText)) : Optional.empty();
            if (!option && !strikeText.isEmpty() && decimal(strikeText).signum() != 0)
                throw new IllegalArgumentException("Unexpected strike");
            return Instrument.create(new BrokerInstrumentId("ZERODHA", Long.toString(token)),
                    required(row, "tradingsymbol"), exchange, segment, type, expiry, strike,
                    decimal(required(row, "tick_size")), lot);
        } catch (RuntimeException invalid) { throw new BrokerReadException(INVALID_RESPONSE); }
    }
    private static String required(Map<String, String> row, String name) {
        String value = row.get(name);
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid required field");
        return value.strip();
    }
    private static BigDecimal decimal(String value) {
        if (!value.matches("[0-9]{1,16}(\\.[0-9]{1,12})?"))
            throw new IllegalArgumentException("Invalid decimal");
        return new BigDecimal(value);
    }
    private static String optionalText(Map<String, String> row, String name) {
        String value = Objects.requireNonNull(row.get(name));
        if (value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid field");
        return value.strip();
    }
    private static LocalDate parseDate(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Invalid expiry");
        return LocalDate.parse(value);
    }
}
