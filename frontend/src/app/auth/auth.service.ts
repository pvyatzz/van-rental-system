import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../environments/environment';

/** What POST /api/auth/login returns (LoginResponse on the backend) */
export interface Session {
  token: string;
  username: string;
  expiresAt: string; // ISO timestamp
}

const STORAGE_KEY = 'vr.session';

/**
 * Keeps the admin's login token. It's stored in localStorage so a page refresh doesn't log you out.
 * Trade-off: any script running on the page could read it, which is why the app must never
 * render untrusted HTML (Angular escapes text by default, which protects against that).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(readStoredSession());

  readonly username = computed(() => this.session()?.username ?? null);
  readonly loggedIn = computed(() => this.session() !== null);

  login(username: string, password: string): Observable<Session> {
    return this.http.post<Session>(`${environment.apiUrl}/auth/login`, { username, password }).pipe(
      tap(session => {
        this.session.set(session);
        try {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        } catch {
          // storage blocked (e.g. private mode): stay logged in for this tab only
        }
      })
    );
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

  isAuthenticated(): boolean {
    return this.token() !== null;
  }
}

function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}
