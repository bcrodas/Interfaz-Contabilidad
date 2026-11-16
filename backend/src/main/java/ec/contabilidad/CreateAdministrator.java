package ec.contabilidad;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CreateAdministrator {
    public static void main(String[] args) throws Exception {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Ejecute este comando en una terminal interactiva.");
        }
        String dbUser = requiredEnvironment("ORACLE_USERNAME");
        String dbPassword = requiredEnvironment("ORACLE_PASSWORD");
        String url = System.getenv().getOrDefault("ORACLE_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        String username = console.readLine("Nombre del administrador de la aplicación: ");
        if (username == null || username.isBlank() || username.trim().length() > 100) {
            throw new IllegalArgumentException("El nombre debe tener entre 1 y 100 caracteres.");
        }
        char[] password = console.readPassword("Contraseña del administrador: ");
        char[] confirmation = console.readPassword("Repita la contraseña: ");
        try {
            if (password == null || confirmation == null || password.length < 12
                    || new String(password).getBytes(StandardCharsets.UTF_8).length > 72
                    || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException(
                    "Las contraseñas deben coincidir, tener al menos 12 caracteres y no superar 72 bytes UTF-8.");
            }
            String hash = new BCryptPasswordEncoder().encode(new String(password));
            try (Connection connection = DriverManager.getConnection(url, dbUser, dbPassword)) {
                connection.setAutoCommit(false);
                try {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("LOCK TABLE USUARIO_APLICACION IN EXCLUSIVE MODE");
                        try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM USUARIO_APLICACION")) {
                            rs.next();
                            if (rs.getLong(1) != 0) {
                                throw new IllegalStateException("Ya existen usuarios; no se creará otro administrador inicial.");
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO USUARIO_APLICACION (ID_USUARIO, NOMBRE_USUARIO, PASSWORD_HASH, ROL, ACTIVO) "
                                    + "VALUES (SEQ_USUARIO_APLICACION.NEXTVAL, ?, ?, 'ADMINISTRADOR', 1)")) {
                        statement.setString(1, username.trim());
                        statement.setString(2, hash);
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            }
            console.printf("Administrador creado correctamente.%n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Debe definir la variable de entorno " + name + ".");
        }
        return value;
    }
}
