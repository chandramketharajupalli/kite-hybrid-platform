package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded test observations only; never serialize entities, properties, sessions or exceptions. */
final class RehearsalEvidence {
    private final Map<String,Object> root=new LinkedHashMap<>();
    private final Map<String,Object> scenarios=new TreeMap<>();
    private final List<Map<String,String>> tests=new ArrayList<>();
    private final Path file=Path.of("target/operator-rehearsal/evidence.json");
    RehearsalEvidence() throws Exception {
        var git=new ProcessBuilder("git","rev-parse","HEAD").redirectErrorStream(true).start();
        if (!git.waitFor(10,TimeUnit.SECONDS)) { git.destroyForcibly(); throw new IllegalStateException("Revision unavailable"); }
        String commit=new String(git.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).strip();
        if (git.exitValue()!=0 || !commit.matches("[a-f0-9]{40}")) throw new IllegalStateException("Revision unavailable");
        root.put("schemaVersion",1); root.put("commit",commit); root.put("scope","DISPOSABLE_LOOPBACK_ONLY");
        var sourceFiles=List.of("src/main/java/com/kitehybrid/platform/bootstrap/OperatorConsoleApplication.java",
                "src/main/java/com/kitehybrid/platform/operator/application/OperatorExecutionService.java",
                "src/main/java/com/kitehybrid/platform/order/application/RuntimeExecutionArming.java",
                "src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/OperatorRehearsalIntegrationTest.java");
        var hashes=new TreeMap<String,String>();
        for (var name:sourceFiles) hashes.put(name,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(name)))));
        root.put("sourceHashes",hashes); root.put("revisionMeaning","BASE_COMMIT_WITH_WORKING_TREE_SOURCE_HASHES");
        root.put("authorizesLiveTrading",false); root.put("productionBrokerMutations",0);
        root.put("developmentDatabaseMutations",0); root.put("scenarios",scenarios); root.put("tests",tests);
        root.put("status","IN_PROGRESS"); write();
    }
    void scenario(String name,Map<String,?> result) { scenarios.put(name,result); }
    void test(String name,String status) { tests.add(Map.of("case",name,"status",status)); }
    void finish() throws Exception {
        boolean failed=tests.isEmpty() || tests.stream().anyMatch(t->!t.get("status").equals("PASSED"));
        root.put("status",failed ? "FAILED_OR_INCOMPLETE" : "PASSED_EXECUTED_CASES");
        root.put("fullRehearsalPresent",scenarios.containsKey("normalStartup") && scenarios.containsKey("success")
                && scenarios.containsKey("recovery_ack-store") && scenarios.containsKey("recovery_response-loss"));
        root.put("executedCases",tests.size()); write();
    }
    private void write() throws Exception {
        Files.createDirectories(file.getParent());
        var json=JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(root);
        // Guard against accidentally widening evidence capture to secret/payload/identifier material.
        if (json.matches("(?s).*(syntheticToken|syntheticSecret|syntheticKey|replacementSynthetic|synthetic-only-broker|jdbc:postgresql|Authorization:|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}).*"))
            throw new IllegalStateException("Unbounded rehearsal evidence");
        Files.writeString(file,json+"\n");
    }
}
