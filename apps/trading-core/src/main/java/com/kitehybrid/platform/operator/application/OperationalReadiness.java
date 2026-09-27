package com.kitehybrid.platform.operator.application;

/** Local evidence only. Implementations must neither repair state nor call the broker. */
@FunctionalInterface
public interface OperationalReadiness {
    Evidence inspect();
    record Evidence(boolean databaseReady, boolean reconciliationStoreHealthy,
                    boolean reconciliationConflictClear, boolean tradingReadAvailable) {}
}
