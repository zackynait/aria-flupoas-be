package it.aria.fluper.auth;

import java.net.URI;
import java.security.MessageDigest;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
public class IdpcAuthController {
    private final IdpcProperties properties;
    private final SecurityContextRepository contextRepository;

    public IdpcAuthController(IdpcProperties properties, SecurityContextRepository contextRepository) {
        this.properties = properties;
        this.contextRepository = contextRepository;
    }

    /** Start browser SSO through the locally installed Shibboleth SP. */
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        requireConfigured();
        URI location = UriComponentsBuilder.fromUriString(properties.getSpLoginUrl())
                .queryParam("target", properties.getCallbackUrl())
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /**
     * Shibboleth-protected target. The ingress proxy must strip client-supplied identity headers
     * and inject the configured verification marker and IDPC attributes after SSO validation.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request, HttpServletResponse response) {
        requireConfigured();
        String marker = request.getHeader(properties.getTrustedProxyHeader());
        if (!constantTimeEquals(properties.getTrustedProxySecret(), marker)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Risposta IDPC non verificata dal proxy SSO");
        }

        String codiceFiscale = clean(request.getHeader(properties.getCodiceFiscaleHeader()));
        String username = clean(request.getHeader(properties.getUsernameHeader()));
        if (codiceFiscale == null || username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Attributi IDPC mancanti");
        }

        // ECM uses nomeUtente values such as "idpc.<username>".
        if (username.startsWith("idpc.")) username = username.substring("idpc.".length());
        AuthenticatedUser user = new AuthenticatedUser(codiceFiscale, username);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, java.util.List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId();
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(properties.getFrontendUrl())).build();
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return Map.of("status", "logged_out");
    }

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    private void requireConfigured() {
        if (!properties.isEnabled() || properties.getTrustedProxySecret() == null
                || properties.getTrustedProxySecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Login IDPC non configurato: attivare l'integrazione Shibboleth e i secret del proxy");
        }
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
