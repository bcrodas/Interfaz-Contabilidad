package ec.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record AccountTypeRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank String nature,
    @NotBlank String classification
) {}

record AccountTypeDto(Long id, String name, String nature, String classification) {}

record AccountRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull Long typeId
) {}

record AccountDto(
    Long id, String code, String name, Long typeId, String typeName,
    String nature, String classification, BigDecimal balance
) {}

record VoucherLineRequest(
    @NotNull Long accountId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal debit,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal credit
) {}

record VoucherRequest(
    @NotNull LocalDate date,
    @NotBlank @Size(max = 200) String observations,
    @NotNull @Size(min = 2) List<@Valid VoucherLineRequest> details
) {}

record VoucherLineDto(
    Long id, Long accountId, String accountCode, String accountName,
    BigDecimal debit, BigDecimal credit
) {}

record VoucherDto(Long id, String number, LocalDate date, String observations, List<VoucherLineDto> details) {}

record BootstrapDto(List<AccountTypeDto> accountTypes, List<AccountDto> accounts, List<VoucherDto> vouchers) {}
