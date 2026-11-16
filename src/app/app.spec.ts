import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { App } from './app';
import { AccountingApiService, BootstrapData } from './accounting-api.service';

describe('App', () => {
  const data: BootstrapData = {
    accountTypes: [
      { id: 1, name: 'Activo', nature: 'DEUDORA', classification: 'ACTIVO' },
      { id: 2, name: 'Ingresos', nature: 'ACREEDORA', classification: 'INGRESO' },
    ],
    accounts: [
      { id: 1, code: '1.1.01', name: 'Caja', typeId: 1, typeName: 'Activo', nature: 'DEUDORA', classification: 'ACTIVO', balance: 0 },
      { id: 2, code: '4.1.01', name: 'Ventas', typeId: 2, typeName: 'Ingresos', nature: 'ACREEDORA', classification: 'INGRESO', balance: 0 },
    ],
    vouchers: [],
  };

  let api: {
    session: ReturnType<typeof vi.fn>;
    bootstrap: ReturnType<typeof vi.fn>;
    createVoucher: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      session: vi.fn(() => of({ username: 'admin', authorities: [] })),
      bootstrap: vi.fn(() => of(data)),
      createVoucher: vi.fn(() => of({
        id: 10,
        number: 'DI-010',
        date: '2026-11-15',
        observations: 'Registro de prueba',
        details: [],
      })),
    };
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{ provide: AccountingApiService, useValue: api }],
    }).compileComponents();
  });

  it('should render accounting data after authenticating the session', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(api.session).toHaveBeenCalled();
    expect(api.bootstrap).toHaveBeenCalled();
    expect(compiled.querySelector('h1')?.textContent).toContain('Tu contabilidad');
    expect(compiled.textContent).toContain('Comprobantes recientes');
  });

  it('shows the login screen when an asynchronous session request returns 401', async () => {
    const session = new Subject<never>();
    api.session.mockReturnValue(session);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Conectando contabilidad');

    session.error(new HttpErrorResponse({ status: 401 }));
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Iniciar sesión');
  });

  it('shows an error when the asynchronous session request fails', async () => {
    const session = new Subject<never>();
    api.session.mockReturnValue(session);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    session.error(new HttpErrorResponse({ status: 500 }));
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Servicio no disponible');
  });

  it('renders data after an asynchronous bootstrap request completes', async () => {
    const bootstrap = new Subject<BootstrapData>();
    api.bootstrap.mockReturnValue(bootstrap);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Conectando contabilidad');

    bootstrap.next(data);
    bootstrap.complete();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Tu contabilidad');
  });

  it('should reject a voucher when debit and credit do not balance', async () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.accountTypes = data.accountTypes;
    app.accounts = data.accounts;
    app.openNewVoucher();
    const draft = app.voucherDraft;
    if (!draft) throw new Error('Voucher editor did not open.');

    draft.observations = 'Registro de prueba';
    draft.details[0] = { id: 1, accountId: 1, accountCode: '1.1.01', debit: 100, credit: 0 };
    draft.details[1] = { id: 2, accountId: 2, accountCode: '4.1.01', debit: 0, credit: 90 };
    app.saveVoucher();

    expect(api.createVoucher).not.toHaveBeenCalled();
    expect(app.formError).toContain('no está cuadrado');
  });

  it('should persist a balanced voucher through the API using account IDs', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.accountTypes = data.accountTypes;
    app.accounts = data.accounts;
    app.openNewVoucher();
    const draft = app.voucherDraft;
    if (!draft) throw new Error('Voucher editor did not open.');

    draft.observations = 'Registro de prueba';
    draft.details[0] = { id: 1, accountId: 1, accountCode: '1.1.01', debit: 100, credit: 0 };
    draft.details[1] = { id: 2, accountId: 2, accountCode: '4.1.01', debit: 0, credit: 100 };
    app.saveVoucher();

    expect(api.createVoucher).toHaveBeenCalledWith({
      date: draft.date,
      observations: 'Registro de prueba',
      details: [
        { accountId: 1, debit: 100, credit: 0 },
        { accountId: 2, debit: 0, credit: 100 },
      ],
    });
    expect(app.modal).toBeNull();
    expect(app.notice).toContain('guardado');
  });

  it('calculates report totals from voucher details and searches those details', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.accounts = data.accounts;
    app.accountTypes = data.accountTypes;
    app.dateFrom = '2026-11-01';
    app.dateTo = '2026-11-30';
    app.vouchers = [{
      id: 1,
      number: 'DI-001',
      date: '2026-11-15',
      observations: 'Venta',
      details: [
        { id: 1, accountId: 1, accountCode: '1.1.01', accountName: 'Caja', debit: 100, credit: 0 },
        { id: 2, accountId: 2, accountCode: '4.1.01', accountName: 'Ventas', debit: 0, credit: 100 },
      ],
    }];

    expect(app.filteredVouchers).toHaveLength(1);
    app.query = 'Caja';
    expect(app.filteredVouchers).toHaveLength(1);
    app.query = '';
    expect(app.accounts[0] ? app.accountNormalBalance(app.accounts[0], null, '2026-11-30') : 0).toBe(100);
    expect(app.accounts[1] ? app.accountNormalBalance(app.accounts[1], '2026-11-01', '2026-11-30') : 0).toBe(100);
  });
});
