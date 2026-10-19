import { Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { GoogleSignInButtonComponent } from '../auth/google-sign-in-button.component';
import { IconComponent } from '../shared/icon.component';
import { problemMessage } from '../shared/problem';

/**
 * One sign-in page for everyone:
 *  - customers: "Continue with Google"
 *  - admin: username + password (folded away under "Admin sign-in")
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, GoogleSignInButtonComponent, IconComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Where to go after signing in (?returnUrl=/book?vehicleId=2), set by the guards / interceptor */
  readonly returnUrl = input<string>();

  /** Open the admin form straight away when the user was heading to an admin page */
  readonly adminOpen = computed(() => this.safeReturnUrl()?.startsWith('/admin') ?? false);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  afterGoogleSignIn(): void {
    const url = this.safeReturnUrl();
    // a customer can't use admin pages, so don't send them back there
    this.router.navigateByUrl(url && !url.startsWith('/admin') ? url : '/my-bookings');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username.trim(), password).subscribe({
      next: () => {
        const url = this.safeReturnUrl();
        this.router.navigateByUrl(url?.startsWith('/admin') ? url : '/admin');
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(problemMessage(err));
        this.form.controls.password.reset();
      }
    });
  }

  /** Only paths inside this app (a returnUrl like "https://evil.example" is ignored) */
  private safeReturnUrl(): string | null {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') ? url : null;
  }
}
