/** A plain Google Maps link for a point. Opens the app on phones; no API key needed for links. */
export function googleMapsUrl(latitude: number, longitude: number): string {
  return `https://www.google.com/maps?q=${latitude},${longitude}`;
}
