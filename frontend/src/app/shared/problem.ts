import { HttpErrorResponse } from '@angular/common/http';

/** The backend's error body (Spring ProblemDetail, RFC 7807). */
interface ProblemDetail {
  status: number;
  title: string;
  detail?: string;
  errors?: Record<string, string>;
}

/** Turns any HTTP error into one readable message for the UI. */
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Something went wrong. Please try again.';
  }
  if (error.status === 0) {
    return 'Cannot reach the server. Is the backend running on port 8080?';
  }
  const problem = error.error as ProblemDetail | null;
  if (problem?.errors) {
    return Object.entries(problem.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join(', ');
  }
  return problem?.detail ?? `Request failed (${error.status})`;
}
