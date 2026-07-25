/** Tailwind config for the Colony Web Dashboard front-end.
 *  Build with:  ./tailwindcss.exe -c tailwind.config.js -i tailwind.input.css -o src/main/resources/webroot/style.css --minify
 */
module.exports = {
    content: ["./src/main/resources/webroot/**/*.{html,js}"],
    theme: {
        extend: {
            colors: {
                ink: { 950: "#070a12", 900: "#0b0f1a", 850: "#0e1320", 800: "#131a2b" },
            },
            fontFamily: {
                sans: ["Inter", "Segoe UI", "Roboto", "system-ui", "sans-serif"],
            },
        },
    },
};
