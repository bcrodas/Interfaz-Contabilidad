import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

export interface AccountType {
  id: number;
  name: string;
  nature: 'DEUDORA' | 'ACREEDORA' | null;
  classification: 'ACTIVO' | 'PASIVO' | 'PATRIMONIO' | 'INGRESO' | 'GASTO' | null;
}

export interface Account {
  id: number;
  code: string;
  name: string;
  typeId: number;
  typeName: string;
  nature: AccountType['nature'];
  classification: AccountType['classification'];
  balance: number;
}

export interface VoucherLine {
  id: number;
  accountId: number;
  accountCode: string;
  accountName: string;
  debit: number;
  credit: number;
}

export interface Voucher {
  id: number;
  number: string;
  date: string;
  observations: string;
  details: VoucherLine[];
}

export interface BootstrapData {
  accountTypes: AccountType[];
  accounts: Account[];
  vouchers: Voucher[];
}

export interface VoucherRequest {
  date: string;
  observations: string;
  details: { accountId: number; debit: number; credit: number }[];
}

export interface AccountTypeRequest {
  name: string;
  nature: 'DEUDORA' | 'ACREEDORA';
  classification: NonNullable<AccountType['classification']>;
}

export interface AccountRequest {
  name: string;
  typeId: number;
}

export interface User {
  id: number;
  username: string;
  role: 'ADMINISTRADOR' | 'USUARIO';
  active: boolean;
}

export interface UserRequest {
  username: string;
  password: string;
  role: User['role'];
  active: boolean;
}

interface Session {
  username: string;
  authorities: string[];
}

@Injectable({ providedIn: 'root' })
export class AccountingApiService {
  private readonly http = inject(HttpClient);

  session(): Observable<Session> {
    return this.http.get<Session>('/api/session');
  }

  bootstrap(): Observable<BootstrapData> {
    return this.http.get<BootstrapData>('/api/bootstrap');
  }

  login(username: string, password: string): Observable<void> {
    const body = new HttpParams().set('username', username).set('password', password).toString();
    return this.http.get('/api/csrf').pipe(
      switchMap(() =>
        this.http.post<void>('/api/login', body, {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        }),
      ),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/logout', {});
  }

  createVoucher(request: VoucherRequest): Observable<Voucher> {
    return this.http.post<Voucher>('/api/vouchers', request);
  }

  updateVoucher(id: number, request: VoucherRequest): Observable<Voucher> {
    return this.http.put<Voucher>(`/api/vouchers/${id}`, request);
  }

  deleteVoucher(id: number): Observable<void> {
    return this.http.delete<void>(`/api/vouchers/${id}`);
  }

  createAccount(request: AccountRequest): Observable<Account> {
    return this.http.post<Account>('/api/accounts', request);
  }

  updateAccount(id: number, request: AccountRequest): Observable<Account> {
    return this.http.put<Account>(`/api/accounts/${id}`, request);
  }

  deleteAccount(id: number): Observable<void> {
    return this.http.delete<void>(`/api/accounts/${id}`);
  }

  createAccountType(request: AccountTypeRequest): Observable<AccountType> {
    return this.http.post<AccountType>('/api/account-types', request);
  }

  updateAccountType(id: number, request: AccountTypeRequest): Observable<AccountType> {
    return this.http.put<AccountType>(`/api/account-types/${id}`, request);
  }

  deleteAccountType(id: number): Observable<void> {
    return this.http.delete<void>(`/api/account-types/${id}`);
  }

  users(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }

  createUser(request: UserRequest): Observable<User> {
    return this.http.post<User>('/api/users', request);
  }

  updateUser(id: number, request: UserRequest): Observable<User> {
    return this.http.put<User>(`/api/users/${id}`, request);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`/api/users/${id}`);
  }
}
