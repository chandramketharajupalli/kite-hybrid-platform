package com.kitehybrid.platform.broker.application.auth;

/** Official broker authentication operations, isolated from the trading application. */
public interface KiteAuthenticationGateway {
    String loginUrl(String state);
    KiteAccessToken exchange(String requestToken);
}
