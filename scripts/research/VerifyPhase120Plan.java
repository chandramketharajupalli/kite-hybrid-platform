import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.domain.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Offline plan/identity bridge check. No broker, auth, DB, Spring startup or execution. */
public final class VerifyPhase120Plan {
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("NO_NETWORK_OR_ARGUMENTS");
        var json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        var universeText = Files.readString(Path.of("research/phase-12.0/universe.json"));
        var universe = json.readTree(universeText);
        var conditional = json.readTree(Files.readString(Path.of("research/phase-12.0/acquisition-plan.json")));
        var universeFingerprint = HistoricalFingerprint.sha256(universeText);
        require(conditional.path("universe_fingerprint").asText().equals(universeFingerprint));
        require(conditional.path("executable_historical_budget").asInt(-1) == 0);
        var node = json.readTree(Files.readString(Path.of("research/phase-11.4/calendar.json")));
        var dates = new TreeMap<LocalDate, TradingCalendar.Day>();
        for (var row : node.path("days")) {
            var sessions = new ArrayList<TradingCalendar.Session>();
            for (var session : row.path("sessions")) sessions.add(new TradingCalendar.Session(
                    LocalTime.parse(session.path("open").asText()), LocalTime.parse(session.path("close").asText())));
            require(dates.put(LocalDate.parse(row.path("date").asText()), new TradingCalendar.Day(
                    TradingCalendar.Status.valueOf(row.path("status").asText()), sessions)) == null);
        }
        var calendar = new TradingCalendar(node.path("version").asText(), node.path("source").asText(), dates);
        require(calendar.fingerprint().equals(universe.path("calendar_fingerprint").asText()));
        var members = new ArrayList<HistoricalCorpusPlan>();
        var evidence = new ArrayList<Map<String, Object>>();
        require(universe.path("members").size() == 5 && conditional.path("members").size() == 5);
        for (int index = 0; index < 5; index++) {
            var row = universe.path("members").get(index);
            var recorded = conditional.path("members").get(index);
            require(recorded.path("identity").equals(row));
            var instrument = Instrument.create(new BrokerInstrumentId(row.path("broker").asText(),
                    row.path("broker_id").asText()), row.path("symbol").asText(), "NSE", "CASH",
                    InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
            require(instrument.id().value().toString().equals(row.path("instrument_id").asText()));
            var plan = new HistoricalCorpusPlan(instrument.id(), LocalDate.of(2026, 2, 2),
                    LocalDate.of(2026, 7, 1), calendar, 99);
            require(recorded.path("maximum_historical_gets").asInt() == 99);
            var chunks = plan.chunks();
            require(chunks.size() == 99 && recorded.path("chunks").size() == 99);
            for (int day = 0; day < chunks.size(); day++) {
                var chunk = chunks.get(day); var expected = recorded.path("chunks").get(day);
                require(expected.path("date").asText().equals(chunk.date().toString()));
                require(expected.path("expected_bars").asInt() == chunk.expectedBars());
                require(expected.path("open").asText().equals(chunk.window().from()
                        .atZone(TradingCalendar.NSE_ZONE).toLocalTime().toString()));
                require(expected.path("close").asText().equals(chunk.window().to()
                        .atZone(TradingCalendar.NSE_ZONE).toLocalTime().toString()));
            }
            members.add(plan);
            evidence.add(Map.of("symbol", instrument.tradingSymbol(), "instrument_id", instrument.id().value().toString(),
                    "plan_fingerprint", plan.fingerprint(), "sessions", chunks.size(),
                    "bars", chunks.stream().mapToInt(HistoricalCorpusPlan.Chunk::expectedBars).sum()));
        }
        var multi = new MultiInstrumentCorpusPlan(universeFingerprint, members, 495);
        var artifact = json.writeValueAsString(Map.of("schema_version", "Phase120JavaPlanVerification.v1",
                "universe_fingerprint", universeFingerprint, "java_plan_fingerprint", multi.fingerprint(),
                "calendar_fingerprint", calendar.fingerprint(), "expected_requests", multi.expectedRequests(),
                "conditional_maximum_requests", multi.maximumRequests(), "historical_requests", 0,
                "state", "OFFLINE_VERIFIED_ACQUISITION_BLOCKED", "members", evidence));
        var target = Path.of("research/phase-12.0/java-plan-verification.json");
        if (Files.exists(target)) require(Files.readString(target).equals(artifact));
        else Files.writeString(target, artifact, StandardOpenOption.CREATE_NEW);
        System.out.println("Offline Java plan verified: 5 members, 495 chunks, 185625 bars, zero broker calls.");
        System.out.println("Java plan fingerprint: " + multi.fingerprint());
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("PHASE120_PLAN_OR_IDENTITY_MISMATCH");
    }
}
