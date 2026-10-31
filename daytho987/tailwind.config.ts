import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        'daytho-black': '#050505',
        'daytho-white': '#f5f5f5',
        'daytho-red': '#ff0000',
        'daytho-gray': '#1a1a1a',
      },
      fontFamily: {
        sans: ['var(--font-inter)'],
        display: ['var(--font-oswald)'],
      },
    },
  },
  plugins: [],
};
export default config;
