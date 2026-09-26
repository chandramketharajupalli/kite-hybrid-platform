package com.kitehybrid.platform.shared.application;

import java.util.Optional;
import java.util.UUID;

/** Non-secret execution view. Identity changes on every session replacement/invalidation. */
public interface ExecutionSession {
    boolean enabled();
    boolean authenticated();
    boolean tokenAvailable();
    /** Nonblocking coherent view: present only for a currently usable, verified session. */
    default Optional<UUID> executionIdentity() { return Optional.empty(); }
}
