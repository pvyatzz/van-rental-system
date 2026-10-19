/**
 * Dates travel as 'YYYY-MM-DD' strings (the same format as <input type="date"> and Java's LocalDate).
 */

/** Today in the user's LOCAL timezone. (new Date().toISOString() would give the UTC date,
 *  which in the Philippines (UTC+8) is still "yesterday" until 8 AM.) */
export function todayIso(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** Inclusive day count, same rule as the backend: 10–12 Nov = 3 days, same day = 1. */
export function rentalDays(startDate: string, endDate: string): number {
  const toUtc = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number);
    return Date.UTC(y, m - 1, d);
  };
  return Math.round((toUtc(endDate) - toUtc(startDate)) / 86_400_000) + 1;
}
