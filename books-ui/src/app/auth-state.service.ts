import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { AuthApi } from './auth-api';
import { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly storageKey = 'books-online-auth';

  protected readonly user = signal<AuthUser | null>(null);
  protected readonly token = signal<string | null>(null);
  protected readonly authChecked = signal(false);
  protected readonly authError = signal('');
  protected readonly isAuthenticating = signal(false);
  protected readonly authUser = computed(() => this.user());
  protected readonly authenticated = computed(() => !!this.token());

  login(request: LoginRequest): Observable<AuthResponse> {
    this.isAuthenticating.set(true);
    this.authError.set('');
    return this.authApi.login(request).pipe(
      tap({
        next: (response) => this.storeSession(response),
        error: (error: HttpErrorResponse) => {
          this.authError.set(this.extractMessage(error));
          this.isAuthenticating.set(false);
        },
        complete: () => this.isAuthenticating.set(false)
      })
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    this.isAuthenticating.set(true);
    this.authError.set('');
    return this.authApi.register(request).pipe(
      tap({
        next: (response) => this.storeSession(response),
        error: (error: HttpErrorResponse) => {
          this.authError.set(this.extractMessage(error));
          this.isAuthenticating.set(false);
        },
        complete: () => this.isAuthenticating.set(false)
      })
    );
  }

  restoreSession(): void {
    if (this.authChecked()) {
      return;
    }

    const raw = localStorage.getItem(this.storageKey);
    if (!raw) {
      this.authChecked.set(true);
      return;
    }

    try {
      const parsed = JSON.parse(raw) as { token: string; user: AuthUser };
      this.token.set(parsed.token);
      this.user.set(parsed.user);
      this.authApi.me(parsed.token).subscribe({
        next: (user) => {
          this.user.set(user);
          this.persist();
          this.authChecked.set(true);
        },
        error: () => {
          this.clearSession();
          this.authChecked.set(true);
          this.router.navigateByUrl('/login');
        }
      });
    } catch {
      this.clearSession();
      this.authChecked.set(true);
    }
  }

  logout(): void {
    const token = this.token();
    this.clearSession();
    if (!token) {
      void this.router.navigateByUrl('/login');
      return;
    }

    this.authApi.logout(token).subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => void this.router.navigateByUrl('/login')
    });
  }

  currentUser(): AuthUser | null {
    return this.authUser();
  }

  isAuthenticated(): boolean {
    return this.authenticated();
  }

  loading(): boolean {
    return this.isAuthenticating();
  }

  errorMessage(): string {
    return this.authError();
  }

  clearError(): void {
    this.authError.set('');
  }

  private storeSession(response: AuthResponse): void {
    this.token.set(response.token);
    this.user.set(response.user);
    this.authChecked.set(true);
    this.persist();
  }

  private clearSession(): void {
    this.token.set(null);
    this.user.set(null);
    localStorage.removeItem(this.storageKey);
  }

  private persist(): void {
    localStorage.setItem(this.storageKey, JSON.stringify({
      token: this.token(),
      user: this.user()
    }));
  }

  private extractMessage(error: HttpErrorResponse): string {
    if (error.error && typeof error.error === 'object' && typeof error.error.message === 'string') {
      return error.error.message;
    }
    if (error.status === 0) {
      return 'Authentication service is unreachable.';
    }
    return 'Authentication request failed.';
  }
}
