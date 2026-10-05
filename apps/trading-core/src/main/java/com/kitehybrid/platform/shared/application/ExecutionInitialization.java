package com.kitehybrid.platform.shared.application;

/** Nonblocking application-initialization evidence for the currently usable execution session. No I/O. */
@FunctionalInterface
public interface ExecutionInitialization {
    boolean initializationReady();
}
