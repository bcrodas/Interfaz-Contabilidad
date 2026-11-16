package ec.contabilidad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:accounting;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class AccountingApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_TIPOCUENTA START WITH 10");
        jdbc.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_CUENTA START WITH 10");
        jdbc.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_CABECERACOMPROBANTE START WITH 10");
        jdbc.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_DETALLECOMPROBANTE START WITH 100");
        jdbc.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_USUARIO_APLICACION START WITH 10");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TIPOCUENTA (ID_TIPO BIGINT PRIMARY KEY, NOMBRE VARCHAR(50), "
            + "NATURALEZA VARCHAR(10), CLASIFICACION VARCHAR(10))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS CUENTA (ID_CUENTA BIGINT PRIMARY KEY, CODIGO VARCHAR(20) UNIQUE, "
            + "NOMBRE VARCHAR(100), ID_TIPO BIGINT REFERENCES TIPOCUENTA(ID_TIPO))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS CABECERACOMPROBANTE (ID_COMPROBANTE BIGINT PRIMARY KEY, "
            + "FECHA DATE, OBSERVACIONES VARCHAR(200))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS DETALLECOMPROBANTE (ID_DETALLE BIGINT PRIMARY KEY, "
            + "ID_COMPROBANTE BIGINT REFERENCES CABECERACOMPROBANTE(ID_COMPROBANTE), "
            + "ID_CUENTA BIGINT REFERENCES CUENTA(ID_CUENTA), DEBE DECIMAL(10,2), HABER DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS USUARIO_APLICACION (ID_USUARIO BIGINT PRIMARY KEY, "
            + "NOMBRE_USUARIO VARCHAR(100) UNIQUE, PASSWORD_HASH VARCHAR(255), ROL VARCHAR(20), ACTIVO INT)");
        jdbc.update("DELETE FROM DETALLECOMPROBANTE");
        jdbc.update("DELETE FROM CABECERACOMPROBANTE");
        jdbc.update("DELETE FROM CUENTA");
        jdbc.update("DELETE FROM TIPOCUENTA");
        jdbc.update("DELETE FROM USUARIO_APLICACION");
        jdbc.update("INSERT INTO TIPOCUENTA VALUES (1, 'Activo', 'DEUDORA', 'ACTIVO')");
        jdbc.update("INSERT INTO TIPOCUENTA VALUES (2, 'Ingresos', 'ACREEDORA', 'INGRESO')");
        jdbc.update("INSERT INTO CUENTA VALUES (1, '1.1.01', 'Caja', 1)");
        jdbc.update("INSERT INTO CUENTA VALUES (2, '4.1.01', 'Ventas', 2)");
    }

    @Test
    void createsAnAccountWithAutomaticPrimaryKeyAndAccountingCode() throws Exception {
        mvc.perform(post("/api/accounts").with(user("admin")).with(csrf())
                .contentType("application/json")
                .content("""
                    {"name":"Banco","typeId":1}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.code").value("1.1.02"))
            .andExpect(jsonPath("$.name").value("Banco"));
    }

    @Test
    void savesBalancedVoucherHeaderAndMultipleDetailsAsOneTransaction() throws Exception {
        mvc.perform(post("/api/vouchers").with(user("admin")).with(csrf())
                .contentType("application/json")
                .content("""
                    {"date":"2026-11-15","observations":"Venta","details":[
                      {"accountId":1,"debit":125.00,"credit":0},
                      {"accountId":2,"debit":0,"credit":100.00},
                      {"accountId":2,"debit":0,"credit":25.00}
                    ]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.details.length()").value(3));
        org.junit.jupiter.api.Assertions.assertEquals(1,
            jdbc.queryForObject("SELECT COUNT(*) FROM CABECERACOMPROBANTE", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(3,
            jdbc.queryForObject("SELECT COUNT(*) FROM DETALLECOMPROBANTE", Integer.class));
    }

    @Test
    void rejectsUnbalancedVoucherWithoutWritingHeaderOrDetails() throws Exception {
        mvc.perform(post("/api/vouchers").with(user("admin")).with(csrf())
                .contentType("application/json")
                .content("""
                    {"date":"2026-11-15","observations":"Descuadrado","details":[
                      {"accountId":1,"debit":125.00,"credit":0},
                      {"accountId":2,"debit":0,"credit":120.00}
                    ]}
                    """))
            .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(0,
            jdbc.queryForObject("SELECT COUNT(*) FROM CABECERACOMPROBANTE", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(0,
            jdbc.queryForObject("SELECT COUNT(*) FROM DETALLECOMPROBANTE", Integer.class));
    }

    @Test
    void updatesVoucherDetailsAndDeletesHeaderAndDetailsTogether() throws Exception {
        jdbc.update("INSERT INTO CABECERACOMPROBANTE VALUES (1, DATE '2026-11-01', 'Inicial')");
        jdbc.update("INSERT INTO DETALLECOMPROBANTE VALUES (1, 1, 1, 20, 0)");
        jdbc.update("INSERT INTO DETALLECOMPROBANTE VALUES (2, 1, 2, 0, 20)");

        mvc.perform(put("/api/vouchers/1").with(user("admin")).with(csrf())
                .contentType("application/json")
                .content("""
                    {"date":"2026-11-15","observations":"Actualizado","details":[
                      {"accountId":1,"debit":40,"credit":0},
                      {"accountId":2,"debit":0,"credit":40}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.observations").value("Actualizado"));
        org.junit.jupiter.api.Assertions.assertEquals(2,
            jdbc.queryForObject("SELECT COUNT(*) FROM DETALLECOMPROBANTE WHERE ID_COMPROBANTE = 1", Integer.class));

        mvc.perform(delete("/api/vouchers/1").with(user("admin")).with(csrf()))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(0,
            jdbc.queryForObject("SELECT COUNT(*) FROM CABECERACOMPROBANTE", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(0,
            jdbc.queryForObject("SELECT COUNT(*) FROM DETALLECOMPROBANTE", Integer.class));
    }

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        mvc.perform(get("/api/bootstrap")).andExpect(status().isUnauthorized());
    }

    @Test
    void restrictsUserManagementToAdministratorsAndPreservesAnActiveAdministrator() throws Exception {
        jdbc.update("INSERT INTO USUARIO_APLICACION VALUES (1, 'admin', 'hash', 'ADMINISTRADOR', 1)");
        jdbc.update("INSERT INTO USUARIO_APLICACION VALUES (2, 'admin2', 'hash', 'ADMINISTRADOR', 1)");

        mvc.perform(get("/api/users").with(user("operador").roles("USUARIO")))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/users").with(user("admin").roles("ADMINISTRADOR")).with(csrf())
                .contentType("application/json")
                .content("""
                    {"username":"operador","password":"contraseña-segura-123","role":"USUARIO","active":true}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("operador"))
            .andExpect(jsonPath("$.role").value("USUARIO"));

        mvc.perform(put("/api/users/1").with(user("admin2").roles("ADMINISTRADOR")).with(csrf())
                .contentType("application/json")
                .content("""
                    {"username":"admin","password":"","role":"USUARIO","active":true}
                    """))
            .andExpect(status().isOk());

        mvc.perform(delete("/api/users/2").with(user("admin").roles("ADMINISTRADOR")).with(csrf()))
            .andExpect(status().isConflict());
    }
}
