import { Component, OnDestroy, output, signal } from '@angular/core';

import { IconComponent } from '../icon.component';

const MAX_BYTES = 5 * 1024 * 1024; // must match the backend (5 MB)
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Pick a screenshot/photo of the payment, see a preview, and get friendly errors before uploading.
 * The browser checks are only for convenience — the backend checks the real file bytes again.
 */
@Component({
  selector: 'app-proof-upload',
  imports: [IconComponent],
  template: `
    <label class="picker">
      <input type="file" accept="image/jpeg,image/png,image/webp" (change)="onPick($event)" />
      <span class="picker-icon"><app-icon name="upload" [size]="20" /></span>
      <span class="picker-text">
        <strong>{{ file() ? 'Choose a different image' : 'Choose screenshot or photo' }}</strong>
        <small>JPG, PNG or WebP · max 5 MB</small>
      </span>
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
    .picker { display: flex; align-items: center; gap: 14px; position: relative; margin: 0; padding: 16px;
      border: 2px dashed var(--c-border-strong); border-radius: var(--r-lg); background: var(--c-surface);
      cursor: pointer; transition: border-color 0.15s, background 0.15s; }
    .picker:hover { border-color: var(--c-primary); background: var(--c-primary-soft); }
    .picker:focus-within { border-color: var(--c-primary); box-shadow: var(--focus-ring); }
    .picker input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .picker-icon { display: inline-grid; place-items: center; width: 44px; height: 44px; border-radius: 12px;
      background: var(--c-primary-soft); color: var(--c-primary); flex-shrink: 0; }
    .picker-text { display: flex; flex-direction: column; }
    .picker-text strong { color: var(--c-text); font-weight: 600; }
    .picker-text small { color: var(--c-text-3); font-weight: 400; }
    .error { color: var(--c-danger); font-size: 0.85rem; margin: 8px 0 0; }
    .preview { display: flex; gap: 14px; align-items: center; margin-top: 12px; padding: 10px;
      border: 1px solid var(--c-border); border-radius: var(--r-md); background: var(--c-surface); }
    .preview img { width: 64px; height: 84px; object-fit: cover; border-radius: var(--r-sm); }
    .preview div { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .preview strong { overflow-wrap: anywhere; font-size: 0.9rem; }
    .preview span { color: var(--c-text-3); font-size: 0.85rem; }
    .preview button { align-self: flex-start; background: none; border: 0; color: var(--c-danger); padding: 0;
      cursor: pointer; font: inherit; font-size: 0.85rem; font-weight: 600; }
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
