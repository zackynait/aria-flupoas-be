package it.aria.fluper.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idpc")
public class IdpcProperties {
    private boolean enabled;
    private String spLoginUrl;
    private String callbackUrl;
    private String frontendUrl;
    private String trustedProxyHeader;
    private String trustedProxySecret;
    private String codiceFiscaleHeader;
    private String usernameHeader;
    private boolean secureCookie;
    private Duration sessionTimeout = Duration.ofMinutes(30);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSpLoginUrl() { return spLoginUrl; }
    public void setSpLoginUrl(String spLoginUrl) { this.spLoginUrl = spLoginUrl; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }
    public String getTrustedProxyHeader() { return trustedProxyHeader; }
    public void setTrustedProxyHeader(String trustedProxyHeader) { this.trustedProxyHeader = trustedProxyHeader; }
    public String getTrustedProxySecret() { return trustedProxySecret; }
    public void setTrustedProxySecret(String trustedProxySecret) { this.trustedProxySecret = trustedProxySecret; }
    public String getCodiceFiscaleHeader() { return codiceFiscaleHeader; }
    public void setCodiceFiscaleHeader(String codiceFiscaleHeader) { this.codiceFiscaleHeader = codiceFiscaleHeader; }
    public String getUsernameHeader() { return usernameHeader; }
    public void setUsernameHeader(String usernameHeader) { this.usernameHeader = usernameHeader; }
    public boolean isSecureCookie() { return secureCookie; }
    public void setSecureCookie(boolean secureCookie) { this.secureCookie = secureCookie; }
    public Duration getSessionTimeout() { return sessionTimeout; }
    public void setSessionTimeout(Duration sessionTimeout) { this.sessionTimeout = sessionTimeout; }
}
