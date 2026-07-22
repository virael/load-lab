// Shared SVG chart scaling, extracted from TestRunnerComponent and
// TestComparisonComponent, which each carried a byte-identical polyline formula. Kept as
// pure functions (no Angular) so they can be unit-tested in isolation — the component
// unit-test builder is not the only way to prove this math.
//
// Viewbox contract shared by both charts: 400 units wide; values map into a 140-unit band
// whose baseline is y=150, so y runs from 150 (value 0) up to 10 (value == max).
const WIDTH = 400;
const BASELINE_Y = 150;
const BAND_HEIGHT = 140;

/** Largest value in the series, floored at 1 so an all-zero series never divides by zero. */
export function seriesMax(values: number[]): number {
  return Math.max(...values, 1);
}

/**
 * Map a numeric series to an SVG polyline `points` string, scaling each value against
 * `max`. An empty series yields an empty string (nothing to draw).
 */
export function toPolyline(values: number[], max: number): string {
  if (values.length === 0) return '';
  const stepX = WIDTH / Math.max(values.length - 1, 1);
  return values.map((v, i) => `${i * stepX},${BASELINE_Y - (v / max) * BAND_HEIGHT}`).join(' ');
}
