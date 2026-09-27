package com.kitehybrid.platform.operator.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("kite.operator-control")
public record OperatorControlConfigurationProperties(@DefaultValue("false") boolean enabled) {}
