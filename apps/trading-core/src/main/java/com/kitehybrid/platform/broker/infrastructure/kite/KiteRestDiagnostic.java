package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.account.application.ValidateBrokerProfileUseCase;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.application.RefreshInstrumentRegistryUseCase;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.observability.KiteReadOperations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Map;

/** Explicit one-shot read-only command. No Spring server, database or scheduled execution. */
public final class KiteRestDiagnostic {
    private KiteRestDiagnostic() {}
    public static void main(String[] arguments) { System.exit(run(arguments, System.getenv(), System.out)); }
    public static int run(String[] arguments, Map<String, String> environment, PrintStream output) {
        if (arguments.length != 1 || !(arguments[0].equals("profile") || arguments[0].equals("instruments"))) {
            output.println("Usage: kiteDiagnostic --args=profile|instruments");
            return 2;
        }
        var metrics = new SimpleMeterRegistry();
        try {
            var properties = KiteProperties.fromEnvironment(environment);
            properties.requireConfigured();
            var session = new KiteSession(properties);
            var transport = KiteRestTransport.production(session);
            var registry = new InMemoryInstrumentRegistry();
            var operations = new KiteReadOperations(
                    new ValidateBrokerProfileUseCase(new KiteProfileAdapter(transport, session)),
                    new RefreshInstrumentRegistryUseCase(new KiteInstrumentMasterAdapter(transport), registry, Clock.systemUTC()),
                    registry, metrics);
            if (arguments[0].equals("profile")) {
                operations.validateProfile();
                output.println("Kite REST: CONNECTED (profile validated; trading remains unavailable)");
            } else {
                var result = operations.refreshInstruments();
                output.println("Instrument records retrieved: " + result.retrievedCount());
                output.println("Registry accepted: " + result.acceptedCount());
                output.println("Registry rejected: " + result.rejectedCount());
                output.println("Snapshot version: " + result.snapshotVersion());
                output.println("Snapshot timestamp: " + result.refreshedAt());
            }
            return 0;
        } catch (BrokerReadException safe) {
            output.println("Kite REST: FAILED (" + safe.category().name() + ")");
            return 1;
        } catch (RuntimeException unexpected) {
            output.println("Kite REST: FAILED (INVALID_RESPONSE)");
            return 1;
        } finally {
            metrics.close();
        }
    }
}
