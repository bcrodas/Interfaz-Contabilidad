package ec.contabilidad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import jakarta.servlet.http.Cookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:security;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class SessionSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS USUARIO_APLICACION "
            + "(ID_USUARIO NUMBER PRIMARY KEY, NOMBRE_USUARIO VARCHAR(100), PASSWORD_HASH VARCHAR(255), "
            + "ROL VARCHAR(20), ACTIVO NUMBER)");
        jdbc.update("DELETE FROM USUARIO_APLICACION");
        jdbc.update("INSERT INTO USUARIO_APLICACION VALUES (1, ?, ?, 'ADMINISTRADOR', 1)",
            "admin", encoder.encode("una-clave-segura"));
        jdbc.update("INSERT INTO USUARIO_APLICACION VALUES (2, ?, ?, 'USUARIO', 0)",
            "inactivo", encoder.encode("una-clave-segura"));
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").param("username", "admin").param("password", "una-clave-segura"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/csrf")).andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void logsInAndLogsOut() throws Exception {
        var result = mvc.perform(post("/api/login").with(csrf())
                .param("username", "admin").param("password", "una-clave-segura"))
            .andExpect(status().isNoContent()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.authorities[0]").value("ROLE_ADMINISTRADOR"));
        mvc.perform(post("/api/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongPasswordAndDisabledUsers() throws Exception {
        mvc.perform(post("/api/login").with(csrf())
                .param("username", "admin").param("password", "incorrecta"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").with(csrf())
                .param("username", "inactivo").param("password", "una-clave-segura"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAngularCookieCsrfHeaderOnLogin() throws Exception {
        var csrfResult = mvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie tokenCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(tokenCookie);

        mvc.perform(post("/api/login")
                .cookie(tokenCookie)
                .header("X-XSRF-TOKEN", tokenCookie.getValue())
                .param("username", "admin")
                .param("password", "una-clave-segura"))
            .andExpect(status().isNoContent());
    }
}
