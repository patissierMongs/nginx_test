# Simplified WAS-3 Spring Boot simulator
FROM python:3.12-alpine

RUN apk add --no-cache curl

WORKDIR /app

# Create a simple HTTP server that mimics Spring Boot actuator endpoints
COPY <<'EOF' /app/server.py
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import os

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/actuator/health' or self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"status": "UP", "service": "was-3-springboot", "type": "Spring Boot (Kotlin)"}
            self.wfile.write(json.dumps(response).encode())
        elif self.path == '/actuator/info' or self.path == '/':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {
                "app": "was-3-springboot",
                "framework": "Spring Boot",
                "language": "Kotlin",
                "version": "3.2.0",
                "java": "21",
                "hostname": os.uname().nodename
            }
            self.wfile.write(json.dumps(response).encode())
        elif self.path.startswith('/api/'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"message": "Hello from WAS-3 Spring Boot", "path": self.path}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        print(f"[WAS-3] {args[0]}")

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', 8080), Handler)
    print('WAS-3 Spring Boot (Kotlin) started on port 8080')
    server.serve_forever()
EOF

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

CMD ["python", "/app/server.py"]
