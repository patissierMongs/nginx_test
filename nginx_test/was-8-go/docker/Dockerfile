# Simplified WAS-8 Go simulator using Python for quick testing
FROM python:3.12-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY <<'EOF' /app/server.py
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import socket

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"status": "UP", "service": "was-8-go", "type": "Go + Fiber"}
            self.wfile.write(json.dumps(response).encode())
        elif self.path == '/' or self.path == '/info':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {
                "app": "was-8-go",
                "framework": "Fiber",
                "language": "Go",
                "version": "1.22",
                "hostname": socket.gethostname()
            }
            self.wfile.write(json.dumps(response).encode())
        elif self.path.startswith('/api/'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"message": "Hello from WAS-8 Go Fiber", "path": self.path}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        print(f"[WAS-8] {args[0]}")

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', 8000), Handler)
    print('WAS-8 Go + Fiber started on port 8000')
    server.serve_forever()
EOF

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["python", "/app/server.py"]
