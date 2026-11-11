/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: { 50: '#fff4ed', 100: '#ffe6d5', 400: '#fb8a4c', 500: '#f4661b', 600: '#e04e0b', 700: '#b93c0b' },
        ink: { 900: '#14201b', 700: '#31423a', 500: '#5d6f66', 300: '#b4c0ba', 100: '#e8eeeb', 50: '#f5f8f6' },
        leaf: { 100: '#dcf5e7', 500: '#17995a', 700: '#0f6e41' },
      },
      fontFamily: { sans: ['Inter', 'system-ui', 'Segoe UI', 'Roboto', 'sans-serif'] },
      boxShadow: { soft: '0 6px 24px -8px rgba(20,32,27,.18)' },
    },
  },
  plugins: [],
};
