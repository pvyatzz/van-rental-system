import { Component, OnDestroy, output, signal } from '@angular/core';

const MAX_BYTES = 5 * 1024 * 1024; // must match the backend (5 MB)
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Pick a screenshot/photo of the payment, see a preview, and get friendly errors before uploading.
 * The browser checks are only for convenience — the backend checks the real file bytes again.
 */
@Component({
  selector: 'app-proof-upload',
  template: `
    <label class="picker">
      <input type="file" accept="image/jpeg,image/png,image/webp" (change)="onPick($event)" />
      <span>{{ file() ? 'Choose a different image' : 'Choose screenshot or photo' }}</span>
    </label>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (previewUrl(); as url) {
      <div class="preview">
        <img [src]="url" alt="Your proof of payment" />
        <div>
          <strong>{{ file()!.name }}</strong>
          <span>{{ sizeLabel() }}</span>
          <button type="button" (click)="clear()">Remove</button>
        </div>
      </div>
    }
  `,
  styles: `
    .picker { display: inline-flex; position: relative; cursor: pointer; }
    .picker input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .picker span { padding: 9px 14px; border: 1px dashed #1f3b57; border-radius: 6px; color: #1f3b57;
      background: #f4f7fb; font-weight: 600; }
    .picker:focus-within span { outline: 2px solid #1f3b57; outline-offset: 2px; }
    .error { color: #b3261e; font-size: 0.85rem; margin: 6px 0 0; }
    .preview { display: flex; gap: 12px; align-items: center; margin-top: 10px; }
    .preview img { width: 90px; height: 120px; object-fit: cover; border-radius: 6px; border: 1px solid #ddd; }
    .preview div { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .preview strong { overflow-wrap: anywhere; font-size: 0.9rem; }
    .preview span { color: #666; font-size: 0.85rem; }
    .preview button { align-self: flex-start; background: none; border: 0; color: #b3261e; padding: 0;
      cursor: pointer; text-decoration: underline; font: inherit; font-size: 0.85rem; }
  `
})
export class ProofUploadComponent implements OnDestroy {
  readonly fileChange = output<File | null>();

  readonly file = signal<File | null>(null);
  readonly previewUrl = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const picked = input.files?.[0] ?? null;
    input.value = ''; // so picking the same file again still fires (change)
    if (!picked) {
      return;
    }
    if (!ACCEPTED.includes(picked.type)) {
      this.error.set('Please choose a JPG, PNG or WebP image (a screenshot works).');
      return;
    }
    if (picked.size > MAX_BYTES) {
      this.error.set('That image is over 5 MB. Please choose a smaller one (a screenshot usually is).');
      return;
    }
    this.error.set(null);
    this.setFile(picked);
  }

  clear(): void {
    this.setFile(null);
  }

  sizeLabel(): string {
    const size = this.file()?.size ?? 0;
    return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(size / 1024)} KB`;
  }

  ngOnDestroy(): void {
    this.revokePreview();
  }

  private setFile(file: File | null): void {
    this.revokePreview();
    this.file.set(file);
    // An object URL lets <img> show the local file without uploading it first
    this.previewUrl.set(file ? URL.createObjectURL(file) : null);
    this.fileChange.emit(file);
  }

  /** Object URLs hold the file in memory until revoked */
  private revokePreview(): void {
    const url = this.previewUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
  }
}
