package com.kitehybrid.platform.marketdata.application;

import com.kitehybrid.platform.marketdata.domain.Tick;

/** Nonblocking bounded handoff. False means overload; caller must record loss/degraded health. */
public interface TickSink {
    boolean offer(Tick tick);
}
