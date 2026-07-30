import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

/**
 * index.html is what the mod serves, so it points at the built `/js/app.js` and `/style.css`.
 * The dev server needs the TypeScript entry instead — main.tsx pulls in the CSS itself, so the
 * stylesheet link goes too, otherwise the last build's CSS would shadow the live one.
 */
function devEntry(): Plugin {
  return {
    name: 'colonyweb-dev-entry',
    apply: 'serve',
    transformIndexHtml(html) {
      return html
        .replace('<link rel="stylesheet" href="/style.css">', '')
        .replace('src="/js/app.js"', 'src="/src/main.tsx"')
    },
  }
}

export default defineConfig({
  plugins: [tailwindcss(), react(), devEntry()],
  root: __dirname,
  base: '/',
  build: {
    outDir: __dirname,
    emptyOutDir: false,
    cssCodeSplit: false,
    rollupOptions: {
      input: resolve(__dirname, 'src/main.tsx'),
      output: {
        entryFileNames: 'js/app.js',
        chunkFileNames: 'js/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          if (assetInfo.name?.endsWith('.css')) return 'style.css'
          return 'assets/[name]-[hash][extname]'
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/auth': 'http://localhost:3001',
      '/api': 'http://localhost:3001',
      '/events': 'http://localhost:3001',
      '/textures': 'http://localhost:3001',
      // The rendered surface PNG, served separately from its metadata document.
      '/map': 'http://localhost:3001',
    },
  },
})
