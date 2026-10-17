// Chart colors. SVG attributes need literal values, so these mirror the CSS tokens in styles.css.

/** Acid lime: the single-series primary for listening measures. */
export const ACCENT = '#c3f04a';

/**
 * Categorical slots for multi-series charts, validated in this order on the #171716 card surface
 * (dark band OKLCH L 0.48 to 0.67, adjacent CVD separation 21.2, normal-vision 24.9, contrast >= 3:1).
 * Stacks must assign them in sequence so neighbours stay distinguishable.
 */
export const SERIES = ['#75a608', '#9572e7', '#e36650', '#3a8ce0', '#c58501'] as const;
export const OTHER = '#57554f';

export const VIOLET = SERIES[1];
export const CORAL = SERIES[2];
export const BLUE = SERIES[3];

export const SURFACE = '#171716';
export const GRID = '#262624';
export const AXIS = '#3b3a36';
export const MUTED = '#8c887e';

/** Sequential lime ramp for heatmaps: zero recedes toward the surface, more is brighter. */
export const HEAT = ['#22221f', '#2c3a12', '#3e5614', '#567617', '#76a01b', '#9ecb2c', '#cbf35c'] as const;

export const tick = { fill: MUTED, fontSize: 11 } as const;
export const axisLine = { stroke: AXIS } as const;
export const barCursor = { fill: 'rgba(239, 235, 226, 0.05)' } as const;
export const lineCursor = { stroke: AXIS, strokeWidth: 1 } as const;
