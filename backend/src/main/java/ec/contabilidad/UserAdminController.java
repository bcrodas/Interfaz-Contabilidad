package ec.contabilidad;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMINISTRADOR')")
class UserAdminController {
    private static final Set<String> ROLES = Set.of("ADMINISTRADOR", "USUARIO");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    UserAdminController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    List<UserDto> list() {
        return jdbc.query("SELECT ID_USUARIO, NOMBRE_USUARIO, ROL, ACTIVO "
                + "FROM USUARIO_APLICACION ORDER BY NOMBRE_USUARIO",
            (rs, row) -> new UserDto(rs.getLong("ID_USUARIO"), rs.getString("NOMBRE_USUARIO"),
                rs.getString("ROL"), rs.getInt("ACTIVO") == 1));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    UserDto create(@Valid @RequestBody UserRequest request) {
        String role = validateRole(request.role());
        String password = validatePassword(request.password());
        long id = jdbc.queryForObject("SELECT SEQ_USUARIO_APLICACION.NEXTVAL FROM DUAL", Long.class);
        try {
            jdbc.update("INSERT INTO USUARIO_APLICACION "
                    + "(ID_USUARIO, NOMBRE_USUARIO, PASSWORD_HASH, ROL, ACTIVO) VALUES (?, ?, ?, ?, ?)",
                id, request.username().trim(), passwordEncoder.encode(password), role, request.active() ? 1 : 0);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe ese nombre de usuario.");
        }
        return new UserDto(id, request.username().trim(), role, request.active());
    }

    @PutMapping("/{id}")
    @Transactional
    UserDto update(@PathVariable long id, @Valid @RequestBody UserRequest request, Authentication currentUser) {
        lockUsers();
        UserDto existing = find(id);
        String role = validateRole(request.role());
        String username = request.username().trim();
        boolean changingCurrentUser = existing.username().equals(currentUser.getName());
        if (changingCurrentUser && (!request.active() || !role.equals("ADMINISTRADOR")
                || !username.equals(existing.username()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No puedes cambiar tu propio nombre, desactivar tu cuenta ni quitarte el rol de administrador.");
        }
        if (existing.role().equals("ADMINISTRADOR") && existing.active()
                && (!request.active() || !role.equals("ADMINISTRADOR")) && activeAdminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe quedar al menos un administrador activo.");
        }
        String password = request.password();
        try {
            if (password == null || password.isBlank()) {
                jdbc.update("UPDATE USUARIO_APLICACION SET NOMBRE_USUARIO = ?, ROL = ?, ACTIVO = ? WHERE ID_USUARIO = ?",
                    username, role, request.active() ? 1 : 0, id);
            } else {
                jdbc.update("UPDATE USUARIO_APLICACION SET NOMBRE_USUARIO = ?, PASSWORD_HASH = ?, ROL = ?, ACTIVO = ? "
                        + "WHERE ID_USUARIO = ?",
                    username, passwordEncoder.encode(validatePassword(password)), role, request.active() ? 1 : 0, id);
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe ese nombre de usuario.");
        }
        return new UserDto(id, username, role, request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@PathVariable long id, Authentication currentUser) {
        lockUsers();
        UserDto user = find(id);
        if (user.username().equals(currentUser.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes eliminar tu propia cuenta.");
        }
        if (user.role().equals("ADMINISTRADOR") && user.active() && activeAdminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe quedar al menos un administrador activo.");
        }
        jdbc.update("DELETE FROM USUARIO_APLICACION WHERE ID_USUARIO = ?", id);
    }

    private UserDto find(long id) {
        return jdbc.query("SELECT ID_USUARIO, NOMBRE_USUARIO, ROL, ACTIVO FROM USUARIO_APLICACION WHERE ID_USUARIO = ?",
                (rs, row) -> new UserDto(rs.getLong("ID_USUARIO"), rs.getString("NOMBRE_USUARIO"),
                    rs.getString("ROL"), rs.getInt("ACTIVO") == 1), id)
            .stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }

    private void lockUsers() {
        jdbc.query("SELECT ID_USUARIO FROM USUARIO_APLICACION "
                + "WHERE ROL = 'ADMINISTRADOR' AND ACTIVO = 1 FOR UPDATE",
            (rs, row) -> rs.getLong(1));
    }

    private int activeAdminCount() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM USUARIO_APLICACION WHERE ROL = 'ADMINISTRADOR' AND ACTIVO = 1", Integer.class);
    }

    private String validateRole(String role) {
        if (role == null || !ROLES.contains(role.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol indicado no es válido.");
        }
        return role.toUpperCase();
    }

    private String validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La contraseña debe tener al menos 12 caracteres y no superar 72 bytes UTF-8.");
        }
        return password;
    }
}
