import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';
import { AccountingApiService, Account as ApiAccount, AccountType as ApiAccountType, User, Voucher as ApiVoucher } from './accounting-api.service';

const SECTION = {
  DASHBOARD: 'dashboard',
  VOUCHERS: 'vouchers',
  ACCOUNTS: 'accounts',
  REPORTS: 'reports',
  USERS: 'users',
} as const;

type Section = (typeof SECTION)[keyof typeof SECTION];

const ACCOUNT_NATURE = {
  DEBIT: 'DEUDORA',
  CREDIT: 'ACREEDORA',
} as const;

type AccountNature = (typeof ACCOUNT_NATURE)[keyof typeof ACCOUNT_NATURE];

const ACCOUNT_TYPE_ID = {
  ASSETS: 'ACTIVO',
  LIABILITIES: 'PASIVO',
  EQUITY: 'PATRIMONIO',
  INCOME: 'INGRESO',
  EXPENSES: 'GASTO',
} as const;

const ACCOUNT_CLASSIFICATIONS = [
  { id: ACCOUNT_TYPE_ID.ASSETS, label: 'Activo' },
  { id: ACCOUNT_TYPE_ID.LIABILITIES, label: 'Pasivo' },
  { id: ACCOUNT_TYPE_ID.EQUITY, label: 'Patrimonio' },
  { id: ACCOUNT_TYPE_ID.INCOME, label: 'Ingresos' },
  { id: ACCOUNT_TYPE_ID.EXPENSES, label: 'Gastos' },
] as const;

const ACCOUNT_VIEW = {
  ACCOUNTS: 'accounts',
  TYPES: 'types',
} as const;

type AccountView = (typeof ACCOUNT_VIEW)[keyof typeof ACCOUNT_VIEW];

const REPORT_VIEW = {
  BALANCE: 'balance',
  INCOME: 'income',
} as const;

type ReportView = (typeof REPORT_VIEW)[keyof typeof REPORT_VIEW];

type AccountType = ApiAccountType;
type Account = ApiAccount;

interface VoucherLine {
  id: number;
  accountCode: string;
  accountId: number | null;
  debit: number;
  credit: number;
}

type Voucher = ApiVoucher;

interface VoucherDraft {
  id: number | null;
  date: string;
  observations: string;
  details: VoucherLine[];
}

interface AccountDraft {
  id: number | null;
  name: string;
  typeId: number;
}

interface AccountTypeDraft {
  id: number | null;
  name: string;
  nature: AccountType['nature'];
  classification: AccountType['classification'];
}

interface MonthActivity {
  label: string;
  income: number;
  expenses: number;
}

@Component({
  selector: 'app-root',
  imports: [DecimalPipe, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  private readonly api = inject(AccountingApiService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly sections = SECTION;
  readonly accountViews = ACCOUNT_VIEW;
  readonly reportViews = REPORT_VIEW;
  readonly accountNatures = ACCOUNT_NATURE;
  readonly accountTypeIds = ACCOUNT_TYPE_ID;
  readonly accountClassifications = ACCOUNT_CLASSIFICATIONS;
  readonly navItems: { id: Section; label: string; icon: string }[] = [
    { id: SECTION.DASHBOARD, label: 'Resumen', icon: 'grid' },
    { id: SECTION.VOUCHERS, label: 'Comprobantes', icon: 'receipt' },
    { id: SECTION.ACCOUNTS, label: 'Plan de cuentas', icon: 'layers' },
    { id: SECTION.REPORTS, label: 'Reportes', icon: 'chart' },
    { id: SECTION.USERS, label: 'Usuarios', icon: 'users' },
  ];

  accountTypes: AccountType[] = [];
  accounts: Account[] = [];
  vouchers: Voucher[] = [];

  activeSection: Section = SECTION.DASHBOARD;
  accountView: AccountView = ACCOUNT_VIEW.ACCOUNTS;
  reportView: ReportView = REPORT_VIEW.BALANCE;
  selectedTypeId: number | '' = '';
  query = '';
  dateFrom = this.monthStart();
  dateTo = this.today();
  modal: 'voucher' | 'account' | 'accountType' | null = null;
  voucherDraft: VoucherDraft | null = null;
  accountDraft: AccountDraft | null = null;
  accountTypeDraft: AccountTypeDraft | null = null;
  formError = '';
  notice = '';
  noticeKind: 'success' | 'error' = 'success';
  mobileMenuOpen = false;
  authStatus: 'loading' | 'login' | 'ready' | 'error' = 'loading';
  loginUsername = '';
  loginPassword = '';
  loginError = '';
  currentUser = '';
  isSaving = false;
  backendError = '';
  isAdministrator = false;
  users: User[] = [];
  userDraft: { id: number | null; username: string; password: string; role: User['role']; active: boolean } | null = null;

  private trackRequest<T>(request: Observable<T>): Observable<T> {
    return request.pipe(finalize(() => this.changeDetector.markForCheck()));
  }

  ngOnInit(): void {
    this.trackRequest(this.api.session()).subscribe({
      next: (session) => {
        this.currentUser = session.username;
        this.isAdministrator = session.authorities.includes('ROLE_ADMINISTRADOR');
        this.loadData();
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 401) {
          this.authStatus = 'login';
        } else {
          this.authStatus = 'error';
          this.backendError = 'No se pudo conectar con el servidor. Verifica que Java y Oracle estén en ejecución.';
        }
      },
    });
  }

  login(): void {
    this.loginError = '';
    this.trackRequest(this.api.login(this.loginUsername.trim(), this.loginPassword)).subscribe({
      next: () => {
        this.loginPassword = '';
        this.trackRequest(this.api.session()).subscribe({
          next: (session) => {
            this.currentUser = session.username;
            this.isAdministrator = session.authorities.includes('ROLE_ADMINISTRADOR');
            this.loadData();
          },
          error: () => {
            this.loginError = 'No se pudo iniciar la sesión.';
            this.authStatus = 'login';
          },
        });
      },
      error: (error: HttpErrorResponse) => {
        this.loginError = error.status === 401
          ? 'Usuario o contraseña incorrectos.'
          : error.status === 0
            ? 'No se pudo conectar con el servidor.'
            : 'No se pudo iniciar la sesión.';
      },
    });
  }

  logout(): void {
    this.trackRequest(this.api.logout()).subscribe({
      next: () => {
        this.accounts = [];
        this.accountTypes = [];
        this.vouchers = [];
        this.currentUser = '';
        this.isAdministrator = false;
        this.users = [];
        this.authStatus = 'login';
      },
      error: () => this.showNotice('No se pudo cerrar la sesión. Inténtalo de nuevo.', 'error'),
    });
  }

  private loadData(): void {
    this.authStatus = 'loading';
    this.trackRequest(this.api.bootstrap()).subscribe({
      next: (data) => {
        this.accountTypes = data.accountTypes;
        this.accounts = data.accounts;
        this.vouchers = data.vouchers.map((voucher) => ({
          ...voucher,
          details: voucher.details.map((line) => ({ ...line, accountId: line.accountId })),
        }));
        if (this.isAdministrator) {
          this.trackRequest(this.api.users()).subscribe({
            next: (users) => {
              this.users = users;
              this.authStatus = 'ready';
            },
            error: () => {
              this.authStatus = 'error';
              this.backendError = 'No se pudieron cargar los usuarios. Verifica que la migración de Oracle esté aplicada.';
            },
          });
        } else {
          this.authStatus = 'ready';
        }
      },
      error: () => {
        this.authStatus = 'error';
        this.backendError = 'No se pudieron cargar los datos. Verifica que Oracle tenga aplicada la migración y que la conexión sea correcta.';
      },
    });
  }

  get filteredUsers(): User[] {
    const search = this.query.trim().toLocaleLowerCase('es');
    return this.users.filter((user) => !search || user.username.toLocaleLowerCase('es').includes(search));
  }

  openNewUser(): void {
    this.userDraft = { id: null, username: '', password: '', role: 'USUARIO', active: true };
    this.formError = '';
  }

  openEditUser(user: User): void {
    this.userDraft = { id: user.id, username: user.username, password: '', role: user.role, active: user.active };
    this.formError = '';
  }

  saveUser(): void {
    const draft = this.userDraft;
    if (!draft || !draft.username.trim()) {
      this.formError = 'Ingresa el nombre de usuario.';
      return;
    }
    if (draft.id === null && draft.password.length < 12) {
      this.formError = 'La contraseña debe tener al menos 12 caracteres.';
      return;
    }
    const request = {
      username: draft.username.trim(),
      password: draft.password,
      role: draft.role,
      active: draft.active,
    };
    const save = draft.id === null
      ? this.api.createUser(request)
      : this.api.updateUser(draft.id, request);
    this.isSaving = true;
    this.trackRequest(save).subscribe({
      next: (user) => {
        this.users = draft.id === null
          ? [...this.users, user].sort((a, b) => a.username.localeCompare(b.username))
          : this.users.map((item) => item.id === user.id ? user : item);
        this.isSaving = false;
        this.userDraft = null;
        this.showNotice('Usuario guardado correctamente.');
      },
      error: (error) => {
        this.isSaving = false;
        this.formError = this.apiErrorMessage(error);
      },
    });
  }

  deleteUser(user: User): void {
    if (!window.confirm(`¿Eliminar el usuario ${user.username}?`)) return;
    this.trackRequest(this.api.deleteUser(user.id)).subscribe({
      next: () => {
        this.users = this.users.filter((item) => item.id !== user.id);
        this.showNotice('Usuario eliminado.');
      },
      error: (error) => this.showNotice(this.apiErrorMessage(error), 'error'),
    });
  }

  private refreshData(onSuccess?: () => void): void {
    this.trackRequest(this.api.bootstrap()).subscribe({
      next: (data) => {
        this.accountTypes = data.accountTypes;
        this.accounts = data.accounts;
        this.vouchers = data.vouchers;
        this.isSaving = false;
        onSuccess?.();
      },
      error: (error) => {
        this.isSaving = false;
        this.showNotice(this.apiErrorMessage(error), 'error');
      },
    });
  }

  private apiErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 0) return 'No se pudo conectar con el servidor.';
    if (error.status === 401) return 'La sesión expiró. Inicia sesión nuevamente.';
    if (error.status === 409) return error.error?.detail ?? error.error?.message ?? 'La operación entra en conflicto con datos existentes.';
    return error.error?.detail ?? error.error?.message ?? 'No se pudo completar la operación.';
  }

  get currentTitle(): string {
    const item = this.navItems.find((navItem) => navItem.id === this.activeSection);
    return item?.label ?? 'Resumen';
  }

  get todayLabel(): string {
    return new Intl.DateTimeFormat('es-EC', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date()).toLocaleUpperCase('es');
  }

  get filteredVouchers(): Voucher[] {
    const search = this.query.trim().toLocaleLowerCase('es');
    return this.vouchers
      .filter((voucher) => voucher.date >= this.dateFrom && voucher.date <= this.dateTo)
      .filter((voucher) =>
        !search ||
        voucher.number.toLocaleLowerCase('es').includes(search) ||
        voucher.observations.toLocaleLowerCase('es').includes(search) ||
        voucher.details.some((line) =>
          line.accountCode.toLocaleLowerCase('es').includes(search) ||
          line.accountName.toLocaleLowerCase('es').includes(search),
        ),
      );
  }

  get filteredAccountTypes(): AccountType[] {
    const search = this.query.trim().toLocaleLowerCase('es');
    return this.accountTypes.filter((type) =>
      !search ||
      type.name.toLocaleLowerCase('es').includes(search) ||
      this.getAccountTypeNature(type.id).toLocaleLowerCase('es').includes(search) ||
      this.getAccountTypeClassification(type.id).toLocaleLowerCase('es').includes(search),
    );
  }

  get filteredAccounts(): Account[] {
    const search = this.query.trim().toLocaleLowerCase('es');
    return this.accounts.filter((account) => {
      const matchesType = !this.selectedTypeId || account.typeId === this.selectedTypeId;
      const matchesSearch =
        !search ||
        account.code.toLocaleLowerCase('es').includes(search) ||
        account.name.toLocaleLowerCase('es').includes(search) ||
        account.typeName.toLocaleLowerCase('es').includes(search);
      return matchesType && matchesSearch;
    });
  }

  get totalDebit(): number {
    return this.voucherDraft?.details.reduce((total, line) => total + this.asAmount(line.debit), 0) ?? 0;
  }

  get totalCredit(): number {
    return this.voucherDraft?.details.reduce((total, line) => total + this.asAmount(line.credit), 0) ?? 0;
  }

  get draftVoucherNumber(): string {
    if (!this.voucherDraft) return '';
    return this.voucherDraft.id === null
      ? 'Se asignará al guardar'
      : this.vouchers.find((voucher) => voucher.id === this.voucherDraft?.id)?.number ?? '';
  }

  get isVoucherBalanced(): boolean {
    return this.toCents(this.totalDebit) > 0 && this.toCents(this.totalDebit) === this.toCents(this.totalCredit);
  }

  get assetsTotal(): number {
    return this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.ASSETS)
      .reduce((total, account) => total + this.balanceAtEndOfPeriod(account), 0);
  }

  get liabilitiesTotal(): number {
    return this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.LIABILITIES)
      .reduce((total, account) => total + this.balanceAtEndOfPeriod(account), 0);
  }

  get equityTotal(): number {
    return this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.EQUITY)
      .reduce((total, account) => total + this.balanceAtEndOfPeriod(account), 0);
  }

  get incomeTotal(): number {
    return this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.INCOME)
      .reduce((total, account) => total + this.balanceForPeriod(account), 0);
  }

  get expensesTotal(): number {
    return this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.EXPENSES)
      .reduce((total, account) => total + this.balanceForPeriod(account), 0);
  }

  get netIncome(): number {
    return this.incomeTotal - this.expensesTotal;
  }

  get balanceDifference(): number {
    return this.assetsTotal - this.liabilitiesTotal - this.equityTotal - this.accumulatedIncomeAtEnd();
  }

  get isBalanceSheetBalanced(): boolean {
    return !this.hasUnclassifiedTypes && Math.abs(this.balanceDifference) <= 0.01;
  }

  get balanceDifferenceLabel(): string {
    if (this.hasUnclassifiedTypes) return 'Clasifica los tipos de cuenta';
    return `Diferencia: ${this.formatCurrency(this.balanceDifference)}`;
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('es-EC', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
    }).format(value);
  }

  formatDate(value: string): string {
    if (!value) return '—';
    return new Intl.DateTimeFormat('es-EC', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    }).format(new Date(`${value}T00:00:00Z`));
  }

  selectSection(section: Section): void {
    this.activeSection = section;
    this.mobileMenuOpen = false;
    this.query = '';
  }

  getAccountName(code: string): string {
    return this.accounts.find((account) => account.code === code)?.name ?? 'Cuenta no disponible';
  }

  getAccountTypeName(typeId: number | string): string {
    const id = typeof typeId === 'number' ? typeId : Number(typeId);
    return this.accountTypes.find((type) => type.id === id)?.name ?? 'Sin tipo';
  }

  getAccountTypeNature(typeId: number): string {
    const nature = this.accountTypes.find((type) => type.id === typeId)?.nature;
    if (nature === ACCOUNT_NATURE.DEBIT) return 'Deudora';
    if (nature === ACCOUNT_NATURE.CREDIT) return 'Acreedora';
    return 'Pendiente';
  }

  getAccountTypeClassification(typeId: number): string {
    const classification = this.accountTypes.find((type) => type.id === typeId)?.classification;
    return this.accountClassifications.find((item) => item.id === classification)?.label ?? 'Pendiente';
  }

  accountCountByType(typeId: number): number {
    return this.accounts.filter((account) => account.typeId === typeId).length;
  }

  accountNormalBalance(account: Account, fromDate: string | null, toDate: string): number {
    if (!account.nature) return 0;
    return this.vouchers
      .filter((voucher) => (!fromDate || voucher.date >= fromDate) && voucher.date <= toDate)
      .flatMap((voucher) => voucher.details)
      .filter((line) => line.accountId === account.id)
      .reduce((balance, line) => balance + (
        account.nature === ACCOUNT_NATURE.DEBIT
          ? line.debit - line.credit
          : line.credit - line.debit
      ), 0);
  }

  balanceAtEndOfPeriod(account: Account): number {
    return this.accountNormalBalance(account, null, this.dateTo);
  }

  balanceForPeriod(account: Account): number {
    return this.accountNormalBalance(account, this.dateFrom, this.dateTo);
  }

  accumulatedIncomeAtEnd(): number {
    const income = this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.INCOME)
      .reduce((total, account) => total + this.accountNormalBalance(account, null, this.dateTo), 0);
    const expenses = this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.EXPENSES)
      .reduce((total, account) => total + this.accountNormalBalance(account, null, this.dateTo), 0);
    return income - expenses;
  }

  get hasUnclassifiedTypes(): boolean {
    return this.accountTypes.some((type) => !type.nature || !type.classification);
  }

  get hasInvalidDateRange(): boolean {
    return !this.dateFrom || !this.dateTo || this.dateFrom > this.dateTo;
  }

  get monthlyActivity(): MonthActivity[] {
    const now = new Date();
    return Array.from({ length: 6 }, (_, index) => {
      const monthDate = new Date(now.getFullYear(), now.getMonth() - 5 + index, 1);
      const start = `${monthDate.getFullYear()}-${String(monthDate.getMonth() + 1).padStart(2, '0')}-01`;
      const nextMonth = new Date(monthDate.getFullYear(), monthDate.getMonth() + 1, 0);
      const end = `${nextMonth.getFullYear()}-${String(nextMonth.getMonth() + 1).padStart(2, '0')}-${String(nextMonth.getDate()).padStart(2, '0')}`;
      const monthlyVouchers = this.vouchers.filter((voucher) => voucher.date >= start && voucher.date <= end);
      const sum = (classification: string) => monthlyVouchers
        .flatMap((voucher) => voucher.details)
        .filter((line) => this.accounts.find((account) => account.id === line.accountId)?.classification === classification)
        .reduce((total, line) => total + line.credit - line.debit, 0);
      return {
        label: new Intl.DateTimeFormat('es-EC', { month: 'short' }).format(monthDate).replace('.', ''),
        income: sum(ACCOUNT_TYPE_ID.INCOME),
        expenses: Math.abs(sum(ACCOUNT_TYPE_ID.EXPENSES)),
      };
    });
  }

  openNewVoucher(): void {
    this.voucherDraft = {
      id: null,
      date: this.today(),
      observations: '',
      details: [this.emptyLine(1), this.emptyLine(2)],
    };
    this.formError = '';
    this.modal = 'voucher';
  }

  openEditVoucher(voucher: Voucher): void {
    this.voucherDraft = {
      id: voucher.id,
      date: voucher.date,
      observations: voucher.observations,
      details: voucher.details.map((line) => ({ ...line })),
    };
    this.formError = '';
    this.modal = 'voucher';
  }

  saveVoucher(): void {
    const draft = this.voucherDraft;
    if (!draft) return;

    const populatedLines = draft.details.filter((line) => line.accountCode);
    if (!draft.date || !draft.observations.trim()) {
      this.formError = 'Completa la fecha y la descripción del comprobante.';
      return;
    }
    if (populatedLines.length < 2) {
      this.formError = 'Agrega al menos dos movimientos con cuenta seleccionada.';
      return;
    }
    if (draft.details.some((line) => !line.accountCode && (this.toCents(this.asAmount(line.debit)) !== 0 || this.toCents(this.asAmount(line.credit)) !== 0))) {
      this.formError = 'Selecciona una cuenta para cada movimiento que tenga un valor registrado.';
      return;
    }
    if (draft.details.some((line) => this.asAmount(line.debit) < 0 || this.asAmount(line.credit) < 0)) {
      this.formError = 'Los valores no pueden ser negativos.';
      return;
    }
    if (populatedLines.some((line) => this.toCents(this.asAmount(line.debit)) > 0 && this.toCents(this.asAmount(line.credit)) > 0)) {
      this.formError = 'Cada movimiento debe registrar un valor en Debe o en Haber, no en ambos.';
      return;
    }
    if (populatedLines.some((line) => this.toCents(this.asAmount(line.debit)) === 0 && this.toCents(this.asAmount(line.credit)) === 0)) {
      this.formError = 'Cada movimiento debe tener un valor mayor a cero.';
      return;
    }
    if (!this.isVoucherBalanced) {
      this.formError = 'El comprobante no está cuadrado. Los valores del Debe y el Haber deben coincidir.';
      return;
    }

    const details = populatedLines.map((line) => {
      const account = this.accounts.find((item) => item.code === line.accountCode);
      if (!account) return null;
      return {
        accountId: account.id,
        debit: this.asAmount(line.debit),
        credit: this.asAmount(line.credit),
      };
    });
    if (details.some((line) => line === null)) {
      this.formError = 'Selecciona una cuenta válida para cada movimiento.';
      return;
    }

    const request = {
      date: draft.date,
      observations: draft.observations.trim(),
      details: details.filter((line) => line !== null),
    };
    this.isSaving = true;
    const save = draft.id === null
      ? this.api.createVoucher(request)
      : this.api.updateVoucher(draft.id, request);
    this.trackRequest(save).subscribe({
      next: () => this.refreshData(() => {
        this.closeModal();
        this.showNotice('Comprobante guardado correctamente.');
      }),
      error: (error) => {
        this.isSaving = false;
        this.formError = this.apiErrorMessage(error);
      },
    });
  }

  addVoucherLine(): void {
    if (!this.voucherDraft) return;
    const nextLineId = Math.max(0, ...this.voucherDraft.details.map((line) => line.id)) + 1;
    this.voucherDraft.details.push(this.emptyLine(nextLineId));
  }

  removeVoucherLine(lineId: number): void {
    if (!this.voucherDraft || this.voucherDraft.details.length <= 2) return;
    this.voucherDraft.details = this.voucherDraft.details.filter((line) => line.id !== lineId);
  }

  deleteVoucher(voucher: Voucher): void {
    if (!window.confirm(`¿Eliminar el comprobante ${voucher.number}?`)) return;
    this.trackRequest(this.api.deleteVoucher(voucher.id)).subscribe({
      next: () => this.refreshData(() => this.showNotice('Comprobante eliminado.')),
      error: (error) => this.showNotice(this.apiErrorMessage(error), 'error'),
    });
  }

  openNewAccount(): void {
    this.accountDraft = {
      id: null,
      name: '',
      typeId: this.accountTypes[0]?.id ?? 0,
    };
    this.formError = '';
    this.modal = 'account';
  }

  openEditAccount(account: Account): void {
    this.accountDraft = {
      id: account.id,
      name: account.name,
      typeId: account.typeId,
    };
    this.formError = '';
    this.modal = 'account';
  }

  saveAccount(): void {
    const draft = this.accountDraft;
    if (!draft) return;
    const name = draft.name.trim();

    if (!name || !draft.typeId) {
      this.formError = 'Completa el nombre y tipo de cuenta.';
      return;
    }
    const request = { name, typeId: draft.typeId };
    const save = draft.id === null
      ? this.api.createAccount(request)
      : this.api.updateAccount(draft.id, request);
    this.isSaving = true;
    this.trackRequest(save).subscribe({
      next: () => this.refreshData(() => {
        this.closeModal();
        this.showNotice('Cuenta guardada correctamente.');
      }),
      error: (error) => {
        this.isSaving = false;
        this.formError = this.apiErrorMessage(error);
      },
    });
  }

  deleteAccount(account: Account): void {
    const isInUse = this.vouchers.some((voucher) =>
      voucher.details.some((line) => line.accountCode === account.code),
    );
    if (isInUse) {
      this.showNotice('No puedes eliminar una cuenta utilizada en un comprobante.', 'error');
      return;
    }
    if (!window.confirm(`¿Eliminar la cuenta ${account.code} — ${account.name}?`)) return;
    this.trackRequest(this.api.deleteAccount(account.id)).subscribe({
      next: () => this.refreshData(() => this.showNotice('Cuenta eliminada.')),
      error: (error) => this.showNotice(this.apiErrorMessage(error), 'error'),
    });
  }

  openNewAccountType(): void {
    this.accountTypeDraft = {
      id: null,
      name: '',
      nature: ACCOUNT_NATURE.DEBIT,
      classification: ACCOUNT_TYPE_ID.ASSETS,
    };
    this.formError = '';
    this.modal = 'accountType';
  }

  openEditAccountType(accountType: AccountType): void {
    this.accountTypeDraft = {
      id: accountType.id,
      name: accountType.name,
      nature: accountType.nature,
      classification: accountType.classification,
    };
    this.formError = '';
    this.modal = 'accountType';
  }

  saveAccountType(): void {
    const draft = this.accountTypeDraft;
    if (!draft) return;
    const name = draft.name.trim();

    if (!name || !draft.classification || !draft.nature) {
      this.formError = 'Completa el nombre, naturaleza y clasificación del tipo de cuenta.';
      return;
    }
    const request = { name, nature: draft.nature, classification: draft.classification };
    const save = draft.id === null
      ? this.api.createAccountType(request)
      : this.api.updateAccountType(draft.id, request);
    this.isSaving = true;
    this.trackRequest(save).subscribe({
      next: () => this.refreshData(() => {
        this.closeModal();
        this.showNotice('Tipo de cuenta guardado correctamente.');
      }),
      error: (error) => {
        this.isSaving = false;
        this.formError = this.apiErrorMessage(error);
      },
    });
  }

  deleteAccountType(accountType: AccountType): void {
    if (this.accounts.some((account) => account.typeId === accountType.id)) {
      this.showNotice('Reasigna las cuentas asociadas antes de eliminar este tipo.', 'error');
      return;
    }
    if (!window.confirm(`¿Eliminar el tipo de cuenta ${accountType.name}?`)) return;
    this.trackRequest(this.api.deleteAccountType(accountType.id)).subscribe({
      next: () => this.refreshData(() => this.showNotice('Tipo de cuenta eliminado.')),
      error: (error) => this.showNotice(this.apiErrorMessage(error), 'error'),
    });
  }

  closeModal(): void {
    this.modal = null;
    this.voucherDraft = null;
    this.accountDraft = null;
    this.accountTypeDraft = null;
    this.formError = '';
  }

  printReport(): void {
    if (this.hasInvalidDateRange || this.hasUnclassifiedTypes) return;
    window.print();
  }

  getBarHeight(value: number): string {
    const max = this.monthlyChartMax;
    return `${Math.max(3, (Math.abs(value) / max) * 100)}%`;
  }

  get monthlyChartMax(): number {
    return Math.max(1, ...this.monthlyActivity.flatMap((month) => [month.income, month.expenses]));
  }

  reportBalance(account: Account): number {
    return this.reportView === REPORT_VIEW.INCOME
      ? this.balanceForPeriod(account)
      : this.balanceAtEndOfPeriod(account);
  }

  get accumulatedProfitAtEnd(): number {
    const income = this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.INCOME)
      .reduce((total, account) => total + this.accountNormalBalance(account, null, this.dateTo), 0);
    const expenses = this.accounts
      .filter((account) => account.classification === ACCOUNT_TYPE_ID.EXPENSES)
      .reduce((total, account) => total + this.accountNormalBalance(account, null, this.dateTo), 0);
    return income - expenses;
  }

  get reportPeriod(): string {
    if (this.dateFrom.slice(0, 7) === this.dateTo.slice(0, 7)) {
      return new Intl.DateTimeFormat('es-EC', { month: 'long', year: 'numeric', timeZone: 'UTC' })
        .format(new Date(`${this.dateFrom.slice(0, 7)}-01T00:00:00Z`));
    }
    return `${this.formatDate(this.dateFrom)} — ${this.formatDate(this.dateTo)}`;
  }

  showNotice(message: string, kind: 'success' | 'error' = 'success'): void {
    this.notice = message;
    this.noticeKind = kind;
  }

  private emptyLine(id: number): VoucherLine {
    return { id, accountCode: '', accountId: null, debit: 0, credit: 0 };
  }

  private asAmount(value: number | string): number {
    const amount = Number(value);
    return Number.isFinite(amount) ? amount : 0;
  }

  private toCents(value: number): number {
    return Math.round(value * 100);
  }

  private today(): string {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60_000;
    return new Date(now.getTime() - offset).toISOString().slice(0, 10);
  }

  private monthStart(): string {
    return `${this.today().slice(0, 7)}-01`;
  }
}
