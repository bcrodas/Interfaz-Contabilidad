package ec.contabilidad;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController {
    @GetMapping("/api/csrf")
    CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/api/session")
    Map<String, Object> session(Authentication authentication) {
        return Map.of("username", authentication.getName(), "authorities",
            authentication.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).toList());
    }

}
