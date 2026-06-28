import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap, catchError, throwError } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly AUTH_URL = 'http://localhost:8081/api/auth';
  private readonly USERS_URL = 'http://localhost:8081/api/users';

  private http = inject(HttpClient);
  private router = inject(Router);

  currentUser = signal<UserResponse | null>(this.loadUserFromStorage());

  private loadUserFromStorage(): UserResponse | null {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  }

  login(request: LoginRequest) {
    return this.http.post<AuthResponse>(`${this.AUTH_URL}/login`, request).pipe(
      tap(res => this.handleAuthSuccess(res)),
      catchError(err => throwError(() => err))
    );
  }

  register(request: RegisterRequest) {
    return this.http.post<AuthResponse>(`${this.AUTH_URL}/register`, request).pipe(
      tap(res => this.handleAuthSuccess(res)),
      catchError(err => throwError(() => err))
    );
  }

  refreshToken() {
    const token = localStorage.getItem('refreshToken');
    if (!token) return throwError(() => new Error('No refresh token'));
    return this.http.post<AuthResponse>(`${this.AUTH_URL}/refresh`, null, {
      headers: { 'X-Refresh-Token': token }
    }).pipe(
      tap(res => this.handleAuthSuccess(res))
    );
  }

  getProfile() {
    return this.http.get<UserResponse>(`${this.USERS_URL}/me`).pipe(
      tap(user => {
        this.currentUser.set(user);
        localStorage.setItem('user', JSON.stringify(user));
      })
    );
  }

  logout() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    this.currentUser.set(null);
    this.router.navigate(['/auth/login']);
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('accessToken');
  }

  getToken(): string | null {
    return localStorage.getItem('accessToken');
  }

  getRole(): string | null {
    return this.currentUser()?.role ?? null;
  }

  private handleAuthSuccess(res: AuthResponse) {
    localStorage.setItem('accessToken', res.accessToken);
    localStorage.setItem('refreshToken', res.refreshToken);
    localStorage.setItem('user', JSON.stringify(res.user));
    this.currentUser.set(res.user);
  }
  getCabinetId(): string | null {
    const user = this.currentUser();
    return user?.cabinetId ?? null;
  }
}
