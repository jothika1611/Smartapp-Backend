package com.smartpay.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Typed access to the app.rate-limit.* configuration. */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private int perMinute = 60;
    private int authPerMinute = 20;

    public int getPerMinute() { return perMinute; }
    public void setPerMinute(int perMinute) { this.perMinute = perMinute; }
    public int getAuthPerMinute() { return authPerMinute; }
    public void setAuthPerMinute(int authPerMinute) { this.authPerMinute = authPerMinute; }
}
