package com.kitehybrid.platform.order.application;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal; import java.time.Duration; import java.util.Set;
/** Immutable execution configuration. Zero caps and an empty allowlist deny execution. */
public record OrderExecutionProperties(boolean enabled, Set<InstrumentId> allowedInstruments, long maxQuantity, BigDecimal maxNotional, Duration riskDecisionMaxAge, Duration marketDataMaxAge, String riskPolicyVersion, String policyVersion) {
 public OrderExecutionProperties { allowedInstruments=Set.copyOf(allowedInstruments==null?Set.of():allowedInstruments); if(maxQuantity<0||maxNotional==null||maxNotional.signum()<0||maxNotional.precision()>36||Math.abs((long)maxNotional.scale())>18||riskDecisionMaxAge==null||riskDecisionMaxAge.isNegative()||marketDataMaxAge==null||marketDataMaxAge.isNegative()) throw new IllegalArgumentException("Invalid execution safety configuration"); if(riskPolicyVersion==null||policyVersion==null) throw new NullPointerException("Policy versions required"); }
 public OrderExecutionProperties(boolean enabled){this(enabled,Set.of(),0,BigDecimal.ZERO,Duration.ZERO,Duration.ZERO,"","phase9-deny");}
 public String auditVersion() {
  var canonical = enabled + "|" + allowedInstruments.stream().map(i -> i.value().toString()).sorted().toList()
          + "|" + maxQuantity + "|" + maxNotional.stripTrailingZeros().toPlainString()
          + "|" + riskDecisionMaxAge + "|" + marketDataMaxAge + "|" + riskPolicyVersion + "|" + policyVersion;
  try { return "execution-v2:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
  catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
 }
}
