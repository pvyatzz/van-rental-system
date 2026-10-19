import { AfterViewInit, Component, ElementRef, NgZone, OnDestroy, inject, output, signal, viewChild } from '@angular/core';
import * as L from 'leaflet';

export interface PickupPoint {
  latitude: number;
  longitude: number;
}

/** Where the map opens before a pin is dropped: Metro Manila. Change this to your area. */
const DEFAULT_CENTER: L.LatLngTuple = [14.5995, 120.9842];
const DEFAULT_ZOOM = 11;

/**
 * Tap/click the map to drop a pickup pin. Uses Leaflet with free OpenStreetMap tiles (no API key).
 * Emits the chosen point (or null when the pin is removed) through `pointChange`.
 */
@Component({
  selector: 'app-pickup-map',
  template: `
    <div #mapElement class="map" aria-label="Map: click to set the pickup point"></div>
    <div class="actions">
      <button type="button" class="btn btn-secondary btn-sm" (click)="useMyLocation()" [disabled]="locating()">
        {{ locating() ? 'Finding you…' : 'Use my location' }}
      </button>
      @if (hasPin()) {
        <button type="button" class="btn btn-ghost btn-sm" (click)="clearPin()">Remove pin</button>
      }
      @if (message()) {
        <span class="message">{{ message() }}</span>
      }
    </div>
  `,
  styles: `
    .map { height: 300px; border-radius: var(--r-lg); border: 1px solid var(--c-border); z-index: 0;
      overflow: hidden; }
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 10px; }
    .message { color: var(--c-text-2); font-size: 0.85rem; }
  `
})
export class PickupMapComponent implements AfterViewInit, OnDestroy {
  private readonly zone = inject(NgZone);
  private readonly mapElement = viewChild.required<ElementRef<HTMLDivElement>>('mapElement');

  readonly pointChange = output<PickupPoint | null>();

  readonly hasPin = signal(false);
  readonly locating = signal(false);
  readonly message = signal<string | null>(null);

  private map?: L.Map;
  private marker?: L.CircleMarker;

  ngAfterViewInit(): void {
    // Leaflet fires many mouse events (every mouse move). Running it outside Angular's zone
    // stops each of those from triggering a change-detection pass over the whole page.
    this.zone.runOutsideAngular(() => {
      this.map = L.map(this.mapElement().nativeElement, { center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM });
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        // OpenStreetMap's licence requires this credit to be visible on the map
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      }).addTo(this.map);
      this.map.on('click', event => this.setPin(event.latlng.lat, event.latlng.lng));
    });
  }

  ngOnDestroy(): void {
    this.map?.remove(); // frees Leaflet's event listeners when leaving the page
  }

  useMyLocation(): void {
    if (!('geolocation' in navigator)) {
      this.message.set('Your browser cannot share its location.');
      return;
    }
    this.locating.set(true);
    this.message.set(null);
    navigator.geolocation.getCurrentPosition(
      position => this.zone.run(() => {
        this.locating.set(false);
        this.setPin(position.coords.latitude, position.coords.longitude);
        this.map?.setView([position.coords.latitude, position.coords.longitude], 16);
      }),
      () => this.zone.run(() => {
        this.locating.set(false);
        this.message.set('Could not get your location. Tap the map instead.');
      }),
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }

  clearPin(): void {
    this.marker?.remove();
    this.marker = undefined;
    this.hasPin.set(false);
    this.pointChange.emit(null);
  }

  private setPin(lat: number, lng: number): void {
    // 6 decimals ≈ 10 cm: more precise than any phone GPS, and keeps the numbers short
    const latitude = Math.round(lat * 1e6) / 1e6;
    const longitude = Math.round(lng * 1e6) / 1e6;
    if (this.map) {
      if (this.marker) {
        this.marker.setLatLng([latitude, longitude]);
      } else {
        // A circle marker is drawn as a vector, so it needs no marker image files
        this.marker = L.circleMarker([latitude, longitude], {
          radius: 9, color: '#ffffff', weight: 3, fillColor: '#d93025', fillOpacity: 1
        }).addTo(this.map);
      }
    }
    // Back inside Angular's zone so the template and the parent form update
    this.zone.run(() => {
      this.hasPin.set(true);
      this.message.set(null);
      this.pointChange.emit({ latitude, longitude });
    });
  }
}
