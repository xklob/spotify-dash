import { createServer } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
const root = resolve('dist'), local = resolve('.local-data/history.json'), metadata = resolve('.local-data/metadata.json');
const port = Number(process.env.PORT || 4187);
if (!existsSync(resolve(root, 'index.html'))) { console.error('Run npm run build before starting the dashboard.'); process.exit(1); }
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.json': 'application/json', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = createServer((req, res) => {
  // Reject DNS-rebinding hostnames before exposing the optional local dataset.
  let host;
  try { host = new URL('http://' + (req.headers.host || '')).hostname; } catch { res.writeHead(400); res.end(); return; }
  if (!['localhost', '127.0.0.1', '[::1]'].includes(host)) { res.writeHead(403); res.end('Local access only'); return; }
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' https://api.reccobeats.com; worker-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
  if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end(); return; }
  let path;
  try { path = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); } catch { res.writeHead(400); res.end(); return; }
  const file = path === '/__local/history.json' ? local : path === '/__local/metadata.json' ? metadata : resolve(root, '.' + (path === '/' ? '/index.html' : path));
  if (file !== local && file !== metadata && !file.startsWith(root + sep)) { res.writeHead(403); res.end(); return; }
  if (!existsSync(file) || !statSync(file).isFile()) { res.writeHead(404); res.end('Not found'); return; }
  res.setHeader('Content-Type', mime[extname(file)] || 'application/octet-stream');
  res.setHeader('Cache-Control', file === local || file === metadata || extname(file) === '.html' ? 'no-store' : 'public, max-age=3600');
  if (req.method === 'HEAD') { res.writeHead(200); res.end(); return; }
  createReadStream(file).pipe(res);
});
server.listen(port, '127.0.0.1', () => console.log(`Spotify Dash: http://localhost:${port}`));
