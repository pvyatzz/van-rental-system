/**
 * Minimal TypeScript types for Google Identity Services (loaded by the <script> in index.html).
 * Only the parts this app uses. Full docs: https://developers.google.com/identity/gsi/web/reference/js-reference
 */
declare namespace google.accounts.id {
  interface CredentialResponse {
    /** The Google ID token (a signed JWT) — sent to our backend for verification */
    credential: string;
  }

  function initialize(config: {
    client_id: string;
    callback: (response: CredentialResponse) => void;
    auto_select?: boolean;
  }): void;

  function renderButton(
    parent: HTMLElement,
    options: {
      type?: 'standard' | 'icon';
      theme?: 'outline' | 'filled_blue' | 'filled_black';
      size?: 'large' | 'medium' | 'small';
      text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
      shape?: 'rectangular' | 'pill' | 'circle' | 'square';
      width?: number;
    }
  ): void;

  function disableAutoSelect(): void;
}
