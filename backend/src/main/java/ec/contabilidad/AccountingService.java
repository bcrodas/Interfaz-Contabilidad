package ec.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountingService {
    private static final String ACCOUNT_TYPES_SQL =
        "SELECT ID_TIPO, NOMBRE, NATURALEZA, CLASIFICACION FROM TIPOCUENTA ORDER BY ID_TIPO";
    private static final String ACCOUNTS_SQL =
        "SELECT C.ID_CUENTA, C.CODIGO, C.NOMBRE, T.ID_TIPO, T.NOMBRE TIPO_NOMBRE, "
        + "T.NATURALEZA, T.CLASIFICACION, "
        + "NVL(SUM(CASE WHEN T.NATURALEZA = 'DEUDORA' THEN NVL(D.DEBE, 0) - NVL(D.HABER, 0) "
        + "WHEN T.NATURALEZA = 'ACREEDORA' THEN NVL(D.HABER, 0) - NVL(D.DEBE, 0) ELSE 0 END), 0) SALDO "
        + "FROM CUENTA C JOIN TIPOCUENTA T ON T.ID_TIPO = C.ID_TIPO "
        + "LEFT JOIN DETALLECOMPROBANTE D ON D.ID_CUENTA = C.ID_CUENTA "
        + "GROUP BY C.ID_CUENTA, C.CODIGO, C.NOMBRE, T.ID_TIPO, T.NOMBRE, T.NATURALEZA, T.CLASIFICACION "
        + "ORDER BY C.CODIGO";
    private static final String VOUCHERS_SQL =
        "SELECT ID_COMPROBANTE, FECHA, OBSERVACIONES FROM CABECERACOMPROBANTE ORDER BY FECHA DESC, ID_COMPROBANTE DESC";

    private final JdbcTemplate jdbc;

    AccountingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    BootstrapDto bootstrap() {
        List<AccountTypeDto> types = jdbc.query(ACCOUNT_TYPES_SQL, (rs, row) ->
            new AccountTypeDto(rs.getLong("ID_TIPO"), rs.getString("NOMBRE"),
                rs.getString("NATURALEZA"), rs.getString("CLASIFICACION")));
        List<AccountDto> accounts = accounts();
        List<VoucherDto> vouchers = jdbc.query(VOUCHERS_SQL, (rs, row) ->
            voucher(rs.getLong("ID_COMPROBANTE"), rs.getDate("FECHA").toLocalDate(), rs.getString("OBSERVACIONES")));
        return new BootstrapDto(types, accounts, vouchers);
    }

    List<AccountTypeDto> accountTypes() {
        return jdbc.query(ACCOUNT_TYPES_SQL, (rs, row) ->
            new AccountTypeDto(rs.getLong("ID_TIPO"), rs.getString("NOMBRE"),
                rs.getString("NATURALEZA"), rs.getString("CLASIFICACION")));
    }

    List<AccountDto> accounts() {
        return jdbc.query(ACCOUNTS_SQL, (rs, row) ->
            new AccountDto(rs.getLong("ID_CUENTA"), rs.getString("CODIGO"), rs.getString("NOMBRE"),
                rs.getLong("ID_TIPO"), rs.getString("TIPO_NOMBRE"), rs.getString("NATURALEZA"),
                rs.getString("CLASIFICACION"), rs.getBigDecimal("SALDO")));
    }

    @Transactional
    AccountTypeDto saveAccountType(Long id, AccountTypeRequest request) {
        String nature = validateChoice(request.nature(), Set.of("DEUDORA", "ACREEDORA"), "naturaleza");
        String classification = validateChoice(request.classification(),
            Set.of("ACTIVO", "PASIVO", "PATRIMONIO", "INGRESO", "GASTO"), "clasificación");
        if (id == null) {
            long newId = nextId("SEQ_TIPOCUENTA");
            jdbc.update("INSERT INTO TIPOCUENTA (ID_TIPO, NOMBRE, NATURALEZA, CLASIFICACION) VALUES (?, ?, ?, ?)",
                newId, request.name().trim(), nature, classification);
            id = newId;
        } else {
            requireExists("SELECT COUNT(*) FROM TIPOCUENTA WHERE ID_TIPO = ?", id, "Tipo de cuenta no encontrado.");
            jdbc.update("UPDATE TIPOCUENTA SET NOMBRE = ?, NATURALEZA = ?, CLASIFICACION = ? WHERE ID_TIPO = ?",
                request.name().trim(), nature, classification, id);
        }
        long savedId = id;
        return accountTypes().stream().filter(type -> type.id().equals(savedId)).findFirst().orElseThrow();
    }

    @Transactional
    void deleteAccountType(long id) {
        requireExists("SELECT COUNT(*) FROM TIPOCUENTA WHERE ID_TIPO = ?", id, "Tipo de cuenta no encontrado.");
        if (count("SELECT COUNT(*) FROM CUENTA WHERE ID_TIPO = ?", id) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reasigna las cuentas antes de eliminar este tipo.");
        }
        jdbc.update("DELETE FROM TIPOCUENTA WHERE ID_TIPO = ?", id);
    }

    @Transactional
    AccountDto saveAccount(Long id, AccountRequest request) {
        requireExists("SELECT COUNT(*) FROM TIPOCUENTA WHERE ID_TIPO = ?", request.typeId(), "Tipo de cuenta no encontrado.");
        long accountId;
        if (id == null) {
            accountId = nextId("SEQ_CUENTA");
            String code = nextAccountCode(request.typeId());
            jdbc.update("INSERT INTO CUENTA (ID_CUENTA, CODIGO, NOMBRE, ID_TIPO) VALUES (?, ?, ?, ?)",
                accountId, code, request.name().trim(), request.typeId());
        } else {
            accountId = id;
            requireExists("SELECT COUNT(*) FROM CUENTA WHERE ID_CUENTA = ?", accountId, "Cuenta no encontrada.");
            jdbc.update("UPDATE CUENTA SET NOMBRE = ?, ID_TIPO = ? WHERE ID_CUENTA = ?",
                request.name().trim(), request.typeId(), accountId);
        }
        return accounts().stream().filter(account -> account.id().equals(accountId)).findFirst().orElseThrow();
    }

    @Transactional
    void deleteAccount(long id) {
        requireExists("SELECT COUNT(*) FROM CUENTA WHERE ID_CUENTA = ?", id, "Cuenta no encontrada.");
        if (count("SELECT COUNT(*) FROM DETALLECOMPROBANTE WHERE ID_CUENTA = ?", id) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta tiene movimientos y no se puede eliminar.");
        }
        jdbc.update("DELETE FROM CUENTA WHERE ID_CUENTA = ?", id);
    }

    List<VoucherDto> vouchers() {
        return jdbc.query(VOUCHERS_SQL, (rs, row) ->
            voucher(rs.getLong("ID_COMPROBANTE"), rs.getDate("FECHA").toLocalDate(), rs.getString("OBSERVACIONES")));
    }

    @Transactional
    VoucherDto saveVoucher(Long id, VoucherRequest request) {
        Set<Long> accountIds = new HashSet<>();
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (VoucherLineRequest line : request.details()) {
            accountIds.add(line.accountId());
            if (line.debit().signum() < 0 || line.credit().signum() < 0
                    || (line.debit().signum() > 0) == (line.credit().signum() > 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada línea debe tener un valor positivo en Debe o en Haber, pero no en ambos.");
            }
            debitTotal = debitTotal.add(line.debit());
            creditTotal = creditTotal.add(line.credit());
        }
        if (debitTotal.signum() <= 0 || debitTotal.compareTo(creditTotal) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comprobante debe estar cuadrado.");
        }
        for (Long accountId : accountIds) {
            requireExists("SELECT COUNT(*) FROM CUENTA WHERE ID_CUENTA = ?", accountId, "Cuenta no encontrada.");
        }
        long voucherId;
        if (id == null) {
            voucherId = nextId("SEQ_CABECERACOMPROBANTE");
            jdbc.update("INSERT INTO CABECERACOMPROBANTE (ID_COMPROBANTE, FECHA, OBSERVACIONES) VALUES (?, ?, ?)",
                voucherId, java.sql.Date.valueOf(request.date()), request.observations().trim());
        } else {
            voucherId = id;
            requireExists("SELECT COUNT(*) FROM CABECERACOMPROBANTE WHERE ID_COMPROBANTE = ?", voucherId,
                "Comprobante no encontrado.");
            jdbc.update("UPDATE CABECERACOMPROBANTE SET FECHA = ?, OBSERVACIONES = ? WHERE ID_COMPROBANTE = ?",
                java.sql.Date.valueOf(request.date()), request.observations().trim(), voucherId);
            jdbc.update("DELETE FROM DETALLECOMPROBANTE WHERE ID_COMPROBANTE = ?", voucherId);
        }
        for (VoucherLineRequest line : request.details()) {
            jdbc.update("INSERT INTO DETALLECOMPROBANTE (ID_DETALLE, ID_COMPROBANTE, ID_CUENTA, DEBE, HABER) "
                    + "VALUES (SEQ_DETALLECOMPROBANTE.NEXTVAL, ?, ?, ?, ?)",
                voucherId, line.accountId(), line.debit(), line.credit());
        }
        return voucher(voucherId, request.date(), request.observations().trim());
    }

    @Transactional
    void deleteVoucher(long id) {
        requireExists("SELECT COUNT(*) FROM CABECERACOMPROBANTE WHERE ID_COMPROBANTE = ?", id,
            "Comprobante no encontrado.");
        jdbc.update("DELETE FROM DETALLECOMPROBANTE WHERE ID_COMPROBANTE = ?", id);
        jdbc.update("DELETE FROM CABECERACOMPROBANTE WHERE ID_COMPROBANTE = ?", id);
    }

    private VoucherDto voucher(long id, LocalDate date, String observations) {
        List<VoucherLineDto> lines = jdbc.query(
            "SELECT D.ID_DETALLE, D.ID_CUENTA, C.CODIGO, C.NOMBRE, D.DEBE, D.HABER "
                + "FROM DETALLECOMPROBANTE D JOIN CUENTA C ON C.ID_CUENTA = D.ID_CUENTA "
                + "WHERE D.ID_COMPROBANTE = ? ORDER BY D.ID_DETALLE",
            (rs, row) -> new VoucherLineDto(rs.getLong("ID_DETALLE"), rs.getLong("ID_CUENTA"),
                rs.getString("CODIGO"), rs.getString("NOMBRE"), rs.getBigDecimal("DEBE"), rs.getBigDecimal("HABER")),
            id);
        return new VoucherDto(id, String.format("DI-%03d", id), date, observations, lines);
    }

    private String nextAccountCode(long typeId) {
        AccountTypeDto type = accountTypes().stream().filter(candidate -> candidate.id() == typeId)
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de cuenta no encontrado."));
        if (type.classification() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Asigna una clasificación contable al tipo antes de crear cuentas.");
        }
        String prefix = switch (type.classification()) {
            case "ACTIVO" -> "1.1.";
            case "PASIVO" -> "2.1.";
            case "PATRIMONIO" -> "3.1.";
            case "INGRESO" -> "4.1.";
            case "GASTO" -> "5.1.";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clasificación contable no válida.");
        };
        int max = 0;
        for (String code : jdbc.query("SELECT CODIGO FROM CUENTA WHERE CODIGO LIKE ?",
                (rs, row) -> rs.getString(1), prefix + "%")) {
            String suffix = code.substring(prefix.length());
            try {
                max = Math.max(max, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // Existing account codes may use a different local numbering convention.
            }
        }
        return prefix + String.format("%02d", max + 1);
    }

    private long nextId(String sequenceName) {
        return jdbc.queryForObject("SELECT " + sequenceName + ".NEXTVAL FROM DUAL", Long.class);
    }

    private int count(String sql, Object value) {
        return jdbc.queryForObject(sql, Integer.class, value);
    }

    private void requireExists(String sql, Object value, String message) {
        if (count(sql, value) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }

    private String validateChoice(String value, Set<String> allowed, String label) {
        if (value == null || !allowed.contains(value.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor de " + label + " no válido.");
        }
        return value.toUpperCase();
    }
}
