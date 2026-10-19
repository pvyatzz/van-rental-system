import { Component, input } from '@angular/core';

export type IconName =
  | 'van' | 'users' | 'calendar' | 'map-pin' | 'menu' | 'x' | 'check' | 'check-circle' | 'shield'
  | 'phone' | 'log-out' | 'arrow-right' | 'alert' | 'clock' | 'lock' | 'upload' | 'wallet';

/**
 * Small inline SVG icons (shapes from the open-source Lucide set, ISC licence).
 * Drawn with currentColor, so an icon takes the text colour of wherever it's placed.
 * Usage: <app-icon name="calendar" [size]="18" />
 */
@Component({
  selector: 'app-icon',
  template: `
    <svg xmlns="http://www.w3.org/2000/svg" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
         aria-hidden="true">
      @switch (name()) {
        @case ('van') {
          <path d="M10 17h4V5H2v12h3" /><path d="M20 17h2v-3.34a4 4 0 0 0-1.17-2.83L19 9h-5" /><path d="M14 17h1" />
          <circle cx="7.5" cy="17.5" r="2.5" /><circle cx="17.5" cy="17.5" r="2.5" />
        }
        @case ('users') {
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
          <path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
        }
        @case ('calendar') {
          <rect width="18" height="18" x="3" y="4" rx="2" /><path d="M16 2v4" /><path d="M8 2v4" /><path d="M3 10h18" />
        }
        @case ('map-pin') {
          <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" /><circle cx="12" cy="10" r="3" />
        }
        @case ('menu') {
          <path d="M4 6h16" /><path d="M4 12h16" /><path d="M4 18h16" />
        }
        @case ('x') {
          <path d="M18 6 6 18" /><path d="m6 6 12 12" />
        }
        @case ('check') {
          <path d="M20 6 9 17l-5-5" />
        }
        @case ('check-circle') {
          <circle cx="12" cy="12" r="10" /><path d="m9 12 2 2 4-4" />
        }
        @case ('shield') {
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /><path d="m9 12 2 2 4-4" />
        }
        @case ('phone') {
          <rect width="14" height="20" x="5" y="2" rx="2" /><path d="M12 18h.01" />
        }
        @case ('log-out') {
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" />
        }
        @case ('arrow-right') {
          <path d="M5 12h14" /><path d="m12 5 7 7-7 7" />
        }
        @case ('alert') {
          <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
          <path d="M12 9v4" /><path d="M12 17h.01" />
        }
        @case ('clock') {
          <circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" />
        }
        @case ('lock') {
          <rect width="18" height="11" x="3" y="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
        }
        @case ('upload') {
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><path d="m17 8-5-5-5 5" /><path d="M12 3v12" />
        }
        @case ('wallet') {
          <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />
          <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />
        }
      }
    </svg>
  `,
  styles: `:host { display: inline-flex; flex-shrink: 0; }`
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(20);
}
