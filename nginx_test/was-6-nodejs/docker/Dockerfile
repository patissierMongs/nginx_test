# Simplified WAS-6 Node.js TypeScript simulator
FROM node:20-alpine

RUN apk add --no-cache curl

WORKDIR /app

RUN cat <<'EOF' > /app/server.js
const http = require('http');
const os = require('os');

const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');

  if (req.url === '/health') {
    res.writeHead(200);
    res.end(JSON.stringify({status: 'UP', service: 'was-6-nodejs', type: 'Node.js (TypeScript)'}));
  } else if (req.url === '/' || req.url === '/info') {
    res.writeHead(200);
    res.end(JSON.stringify({
      app: 'was-6-nodejs',
      framework: 'Express.js',
      language: 'TypeScript',
      nodeVersion: process.version,
      hostname: os.hostname()
    }));
  } else if (req.url.startsWith('/api/')) {
    res.writeHead(200);
    res.end(JSON.stringify({message: 'Hello from WAS-6 Node.js (TypeScript)', path: req.url}));
  } else {
    res.writeHead(404);
    res.end(JSON.stringify({error: 'Not Found'}));
  }
});

server.listen(3000, '0.0.0.0', () => {
  console.log('WAS-6 Node.js (TypeScript) started on port 3000');
});
EOF

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3000/health || exit 1

CMD ["node", "/app/server.js"]
