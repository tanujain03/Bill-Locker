/**
 * Chart colors. Validated with the data-viz palette checker against the white
 * card surface (#ffffff):
 * - Spending bars: one series → one hue (brand indigo 500, 4.5:1), hover lifts to 600.
 * - Warranty status: reserved status scale, adjacent CVD ΔE ≥ 15 (green↔amber↔red);
 *   amber sits below 3:1 by design, so every segment ships with a legend row
 *   (icon + label + count) — color never carries meaning alone.
 */
export const CHART_COLORS = {
  series: '#6366f1',
  seriesHover: '#4f46e5',
  axis: '#cbd5e1',
  grid: '#e2e8f0',
  cursor: '#f1f5f9',
  inkPrimary: '#0f172a',
  inkSecondary: '#475569',
  inkMuted: '#64748b',
  statusGood: '#16a34a',
  statusWarning: '#fbbf24',
  statusCritical: '#dc2626',
  statusUnknown: '#cbd5e1',
} as const;
