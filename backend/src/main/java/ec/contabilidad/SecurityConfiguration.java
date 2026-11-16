package ec.contabilidad;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return username -> jdbc.query(
                "SELECT NOMBRE_USUARIO, PASSWORD_HASH, ROL, ACTIVO FROM USUARIO_APLICACION WHERE NOMBRE_USUARIO = ?",
                (rs, row) -> User.withUsername(rs.getString("NOMBRE_USUARIO"))
                        .password(rs.getString("PASSWORD_HASH"))
                        .roles(rs.getString("ROL"))
                        .disabled(rs.getInt("ACTIVO") != 1)
                        .build(),
                username).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/csrf", "/api/login").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginProcessingUrl("/api/login")
                .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                .failureHandler((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                (request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));
        return http.build();
    }

    private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                Supplier<CsrfToken> deferredCsrfToken) {
            xor.handle(request, response, deferredCsrfToken);
            deferredCsrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String header = request.getHeader(csrfToken.getHeaderName());
            return StringUtils.hasText(header)
                ? plain.resolveCsrfTokenValue(request, csrfToken)
                : xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
