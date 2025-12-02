# Simplified WAS-5 Node.js simulator
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
    res.end(JSON.stringify({status: 'UP', service: 'was-5-nodejs', type: 'Node.js (JavaScript)'}));
  } else if (req.url === '/' || req.url === '/info') {
    res.writeHead(200);
    res.end(JSON.stringify({
      app: 'was-5-nodejs',
      framework: 'Express.js',
      language: 'JavaScript (ESM)',
      nodeVersion: process.version,
      hostname: os.hostname()
    }));
  } else if (req.url.startsWith('/api/')) {
    res.writeHead(200);
    res.end(JSON.stringify({message: 'Hello from WAS-5 Node.js', path: req.url}));
  } else {
    res.writeHead(404);
    res.end(JSON.stringify({error: 'Not Found'}));
  }
});

server.listen(3000, '0.0.0.0', () => {
  console.log('WAS-5 Node.js (JavaScript) started on port 3000');
});
EOF

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3000/health || exit 1

CMD ["node", "/app/server.js"]
