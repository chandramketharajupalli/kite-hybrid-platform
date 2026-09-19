package com.kitehybrid.platform.account.application;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import java.util.Objects;

public final class ValidateBrokerProfileUseCase {
    private final BrokerProfileProvider provider;
    public ValidateBrokerProfileUseCase(BrokerProfileProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }
    public BrokerProfile validate() { return Objects.requireNonNull(provider.currentProfile()); }
}
