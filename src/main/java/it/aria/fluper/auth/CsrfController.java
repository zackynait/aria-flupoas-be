package it.aria.fluper.auth;

import java.util.Map;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CsrfController {
    @GetMapping("/csrf")
    public Map<String, String> token(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }
}
