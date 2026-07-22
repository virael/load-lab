import { describe, expect, it } from 'vitest';
import { seriesMax, toPolyline } from './chart-scale';

// These expected strings are computed by hand from the exact formula the two components
// used inline before extraction (stepX = 400/max(len-1,1); y = 150 - (v/max)*140), so the
// spec pins the current rendered output and would catch any transcription drift.
describe('chart-scale', () => {
  describe('seriesMax', () => {
    it('floors at 1 for an empty series', () => {
      expect(seriesMax([])).toBe(1);
    });

    it('floors at 1 when every value is zero', () => {
      expect(seriesMax([0, 0])).toBe(1);
    });

    it('returns the largest value otherwise', () => {
      expect(seriesMax([5, 3, 9, 2])).toBe(9);
    });
  });

  describe('toPolyline', () => {
    it('returns an empty string for an empty series', () => {
      expect(toPolyline([], 100)).toBe('');
    });

    it('places a single point at x=0, scaled against max', () => {
      // stepX = 400 / max(0,1) = 400; y = 150 - (50/100)*140 = 80
      expect(toPolyline([50], 100)).toBe('0,80');
    });

    it('spans the full width for two points — bottom then top of the band', () => {
      // y(0) = 150; y(max) = 150 - 140 = 10
      expect(toPolyline([0, 100], 100)).toBe('0,150 400,10');
    });

    it('evenly spaces three points and scales each', () => {
      // stepX = 400 / 2 = 200; y = 150 - (v/100)*140
      expect(toPolyline([25, 50, 75], 100)).toBe('0,115 200,80 400,45');
    });
  });
});
