import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { createReadStream, existsSync } from 'node:fs';
import { resolve } from 'node:path';
export default defineConfig({
  plugins: [react(), {
    name: 'local-history',
    configureServer(server) {
      server.middlewares.use('/__local/metadata.json', (_req, res) => {
        const file = resolve('.local-data/metadata.json');
        if (!existsSync(file)) { res.statusCode = 404; res.end(); return; }
        res.setHeader('Content-Type', 'application/json'); res.setHeader('Cache-Control', 'no-store'); createReadStream(file).pipe(res);
      });
      server.middlewares.use('/__local/history.json', (_req, res) => {
        const file = resolve('.local-data/history.json');
        if (!existsSync(file)) { res.statusCode = 404; res.end(); return; }
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Cache-Control', 'no-store');
        createReadStream(file).pipe(res);
      });
    }
  }],
  base: './',
  build: { chunkSizeWarningLimit: 900 },
  server: { host: '127.0.0.1', port: 4187, strictPort: true }
});
