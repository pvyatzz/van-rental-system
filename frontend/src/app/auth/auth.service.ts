import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../environments/environment';

export type Role = 'ADMIN' | 'CUSTOMER';

/** What POST /api/auth/login (admin) and /api/auth/google (customer) return */
export interface Session {
  token: string;
  displayName: string;
  email: string | null;
  roles: Role[];
  expiresAt: string; // ISO timestamp
}

const STORAGE_KEY = 'vr.session';

/**
 * Keeps the login token for either an admin or a customer (one at a time).
 * It's stored in localStorage so a page refresh doesn't log you out.
 * Trade-off: any script running on the page could read it, which is why the app must never
 * render untrusted HTML (Angular escapes text by default, which protects against that).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(readStoredSession());

  readonly displayName = computed(() => this.session()?.displayName ?? null);
  readonly email = computed(() => this.session()?.email ?? null);
  readonly loggedIn = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.roles.includes('ADMIN') ?? false);
  readonly isCustomer = computed(() => this.session()?.roles.includes('CUSTOMER') ?? false);

  /** Admin: username + password */
  login(username: string, password: string): Observable<Session> {
    return this.http.post<Session>(`${environment.apiUrl}/auth/login`, { username, password })
      .pipe(tap(session => this.store(session)));
  }

  /** Customer: the ID token from the Google button, verified by our backend */
  loginWithGoogle(credential: string): Observable<Session> {
    return this.http.post<Session>(`${environment.apiUrl}/auth/google`, { credential })
      .pipe(tap(session => this.store(session)));
  }

  logout(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  /** The token, or null if not logged in or expired (an expired session is cleared). */
  token(): string | null {
    const session = this.session();
    if (!session) {
      return null;
    }
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      this.logout();
      return null;
    }
    return session.token;
  }

  hasRole(role: Role): boolean {
    return this.token() !== null && (this.session()?.roles.includes(role) ?? false);
  }

  private store(session: Session): void {
    this.session.set(session);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // storage blocked (e.g. private mode): stay logged in for this tab only
    }
  }
}

function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const session = raw ? (JSON.parse(raw) as Session) : null;
    // Sessions saved by the older version had no roles; treat them as logged out
    return session && Array.isArray(session.roles) ? session : null;
  } catch {
    return null;
  }
}
