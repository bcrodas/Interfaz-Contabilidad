package ec.contabilidad;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api")
class AccountingController {
    private final AccountingService service;

    AccountingController(AccountingService service) {
        this.service = service;
    }

    @GetMapping("/bootstrap")
    BootstrapDto bootstrap() {
        return service.bootstrap();
    }

    @GetMapping("/account-types")
    List<AccountTypeDto> accountTypes() {
        return service.accountTypes();
    }

    @PostMapping("/account-types")
    @ResponseStatus(HttpStatus.CREATED)
    AccountTypeDto createAccountType(@Valid @RequestBody AccountTypeRequest request) {
        return service.saveAccountType(null, request);
    }

    @PutMapping("/account-types/{id}")
    AccountTypeDto updateAccountType(@PathVariable long id, @Valid @RequestBody AccountTypeRequest request) {
        return service.saveAccountType(id, request);
    }

    @DeleteMapping("/account-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccountType(@PathVariable long id) {
        service.deleteAccountType(id);
    }

    @GetMapping("/accounts")
    List<AccountDto> accounts() {
        return service.accounts();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    AccountDto createAccount(@Valid @RequestBody AccountRequest request) {
        return service.saveAccount(null, request);
    }

    @PutMapping("/accounts/{id}")
    AccountDto updateAccount(@PathVariable long id, @Valid @RequestBody AccountRequest request) {
        return service.saveAccount(id, request);
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@PathVariable long id) {
        service.deleteAccount(id);
    }

    @GetMapping("/vouchers")
    List<VoucherDto> vouchers() {
        return service.vouchers();
    }

    @PostMapping("/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    VoucherDto createVoucher(@Valid @RequestBody VoucherRequest request) {
        return service.saveVoucher(null, request);
    }

    @PutMapping("/vouchers/{id}")
    VoucherDto updateVoucher(@PathVariable long id, @Valid @RequestBody VoucherRequest request) {
        return service.saveVoucher(id, request);
    }

    @DeleteMapping("/vouchers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteVoucher(@PathVariable long id) {
        service.deleteVoucher(id);
    }
}
