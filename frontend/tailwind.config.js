/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      // PHASE 2: named so chart components (CategoryDonutChart etc.) can
      // reference `chart.cat1`..`chart.cat15` from one place instead of
      // repeating hex literals -- the actual values live in
      // src/utils/chartColors.ts (Recharts takes color props as plain
      // strings, not Tailwind classes, so the palette has to exist as a JS
      // array either way; these theme tokens are for any non-chart UI,
      // like a small color swatch/legend dot, that *can* use a Tailwind
      // class and should stay visually in sync with the charts).
      colors: {
        chart: {
          cat1: '#6366f1',
          cat2: '#10b981',
          cat3: '#f59e0b',
          cat4: '#ef4444',
          cat5: '#06b6d4',
          cat6: '#8b5cf6',
          cat7: '#ec4899',
          cat8: '#84cc16',
          cat9: '#f97316',
          cat10: '#14b8a6',
          cat11: '#a855f7',
          cat12: '#eab308',
          cat13: '#f43f5e',
          cat14: '#0ea5e9',
          cat15: '#22c55e',
        },
      },
    },
  },
  plugins: [],
};
