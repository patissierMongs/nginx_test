# Simplified WAS-7 Flask simulator
FROM python:3.12-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY <<'EOF' /app/server.py
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import os
import socket

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"status": "UP", "service": "was-7-flask", "type": "Flask + Gunicorn"}
            self.wfile.write(json.dumps(response).encode())
        elif self.path == '/' or self.path == '/info':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {
                "app": "was-7-flask",
                "framework": "Flask",
                "wsgi": "Gunicorn",
                "language": "Python",
                "version": "3.12",
                "hostname": socket.gethostname()
            }
            self.wfile.write(json.dumps(response).encode())
        elif self.path.startswith('/api/'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"message": "Hello from WAS-7 Flask", "path": self.path}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        print(f"[WAS-7] {args[0]}")

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', 5000), Handler)
    print('WAS-7 Flask + Gunicorn started on port 5000')
    server.serve_forever()
EOF

EXPOSE 5000

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:5000/health || exit 1

CMD ["python", "/app/server.py"]
