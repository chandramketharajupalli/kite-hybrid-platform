package com.kitehybrid.platform.order.application;
import java.time.Duration; import java.time.Instant; import java.util.concurrent.atomic.AtomicReference; import io.micrometer.core.instrument.MeterRegistry;
public final class RuntimeExecutionArming { private final AtomicReference<Instant> until = new AtomicReference<>(); private final MeterRegistry metrics;
 public RuntimeExecutionArming(){this(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());} public RuntimeExecutionArming(MeterRegistry metrics){this.metrics=metrics;}
 public boolean armed(Instant now) { var v=until.get(); if(v==null||!now.isBefore(v)){if(v!=null)metrics.counter("execution.disarmed").increment();until.set(null);return false;} return true;}
 public void arm(Duration duration, Instant now){if(duration==null||duration.isNegative()||duration.isZero())throw new IllegalArgumentException("Invalid arm duration"); until.set(now.plus(duration));metrics.counter("execution.armed").increment();}
 public void disarm(){if(until.getAndSet(null)!=null)metrics.counter("execution.disarmed").increment();} }
