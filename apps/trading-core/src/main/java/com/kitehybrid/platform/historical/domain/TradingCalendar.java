package com.kitehybrid.platform.historical.domain;

import java.time.*;
import java.util.*;

/** Explicit versioned evidence. Missing dates are UNKNOWN, including weekends. */
public record TradingCalendar(String version, String source, Map<LocalDate, Day> days) {
    public static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");
    public enum Status { EXPECTED_SESSION, NON_TRADING_DAY, UNKNOWN_SESSION }
    public record Session(LocalTime open, LocalTime close) {
        public Session {
            if(open==null || close==null || !open.isBefore(close) || open.getSecond()!=0
                    || close.getSecond()!=0 || open.getNano()!=0 || close.getNano()!=0)
                throw new IllegalArgumentException("Invalid session window");
        }
    }
    public record Day(Status status, List<Session> sessions) {
        public Day {
            Objects.requireNonNull(status); sessions=List.copyOf(sessions);
            if ((status==Status.EXPECTED_SESSION) != !sessions.isEmpty())
                throw new IllegalArgumentException("Session evidence mismatch");
            LocalTime previous=null;
            for(var session:sessions) {
                if(previous!=null && session.open().isBefore(previous))
                    throw new IllegalArgumentException("Overlapping or unordered sessions");
                previous=session.close();
            }
        }
    }
    public TradingCalendar {
        if(version==null || !version.matches("[A-Za-z0-9_.-]{1,80}") || source==null
                || source.isBlank() || source.length()>512 || source.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Calendar provenance required");
        days=Map.copyOf(days);
    }
    public Day day(LocalDate date) {
        return days.getOrDefault(date,new Day(Status.UNKNOWN_SESSION,List.of()));
    }
    /** Content identity includes provenance and all explicit date/session evidence. */
    public String fingerprint() {
        var text=new StringBuilder(version).append('\n').append(source).append('\n');
        new TreeMap<>(days).forEach((date,day)->text.append(date).append('=').append(day).append('\n'));
        return HistoricalFingerprint.sha256(text.toString());
    }
}
