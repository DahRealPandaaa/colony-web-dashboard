/** Tailwind config for the ColonyWeb dashboard front-end.
 *
 *  Build with:
 *    ./tailwindcss.exe -c tailwind.config.js -i tailwind.input.css -o src/main/resources/webroot/style.css --minify
 *
 *  Most visual decisions live in the component layer of tailwind.input.css rather than in
 *  markup, so the HTML partials stay readable and the look stays consistent across tabs.
 */
module.exports = {
    content: ["./src/main/resources/webroot/**/*.{html,js}"],
    theme: {
        extend: {
            colors: {
                // Page and surface ramp, darkest first. Deliberately lifted off pure black —
                // the near-black version read as a void behind the panels rather than a page.
                ink: {
                    950: "#121722",
                    900: "#191F2C",
                    850: "#1E2634",
                    800: "#252E3E",
                    700: "#2F3A4C",
                    600: "#3C4859",
                },
                // Hairlines and dividers.
                line: {
                    DEFAULT: "#303B4C",
                    strong: "#414F63",
                },
                accent: {
                    DEFAULT: "#38BDF8",
                    soft: "#93DBFC",
                    deep: "#2563EB",
                },
            },
            fontFamily: {
                sans: ["Inter", "Segoe UI", "Roboto", "system-ui", "sans-serif"],
                mono: ["ui-monospace", "Cascadia Mono", "Segoe UI Mono", "DejaVu Sans Mono", "Menlo", "monospace"],
            },
            boxShadow: {
                panel: "0 1px 0 0 rgba(255,255,255,0.03) inset, 0 12px 32px -16px rgba(0,0,0,0.9)",
                lift: "0 18px 40px -20px rgba(0,0,0,0.95)",
                glow: "0 0 24px -6px rgba(56,189,248,0.45)",
            },
            keyframes: {
                "fade-up": {
                    from: { opacity: "0", transform: "translateY(4px)" },
                    to: { opacity: "1", transform: "none" },
                },
                pulseSoft: {
                    "0%, 100%": { opacity: "1" },
                    "50%": { opacity: "0.45" },
                },
            },
            animation: {
                "fade-up": "fade-up .18s ease-out both",
                "pulse-soft": "pulseSoft 2s ease-in-out infinite",
            },
        },
    },
};
