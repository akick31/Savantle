/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        sv: {
          bg: 'var(--sv-bg)',
          card: 'var(--sv-card)',
          accent: 'var(--sv-accent)',
          green: 'var(--sv-green)',
          yellow: 'var(--sv-yellow)',
          orange: 'var(--sv-orange)',
          red: 'var(--sv-red)',
          muted: 'var(--sv-muted)',
          border: 'var(--sv-border)',
          text: 'var(--sv-text)',
          // Percentile ring colors
          p100: 'var(--sv-p100)',
          p80: 'var(--sv-p80)',
          p50: 'var(--sv-p50)',
          p20: 'var(--sv-p20)',
          p0: 'var(--sv-p0)',
        },
      },
    },
  },
  plugins: [],
};
