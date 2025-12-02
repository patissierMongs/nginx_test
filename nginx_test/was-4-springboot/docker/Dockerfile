# Simplified WAS-4 Spring Boot simulator
FROM python:3.12-alpine

RUN apk add --no-cache curl

WORKDIR /app

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
            response = {"status": "UP", "service": "was-4-springboot", "type": "Spring Boot (Java)"}
            self.wfile.write(json.dumps(response).encode())
        elif self.path == '/actuator/info' or self.path == '/':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {
                "app": "was-4-springboot",
                "framework": "Spring Boot",
                "language": "Java",
                "version": "3.2.0",
                "java": "21",
                "hostname": os.uname().nodename
            }
            self.wfile.write(json.dumps(response).encode())
        elif self.path.startswith('/api/'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"message": "Hello from WAS-4 Spring Boot", "path": self.path}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        print(f"[WAS-4] {args[0]}")

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', 8080), Handler)
    print('WAS-4 Spring Boot (Java) started on port 8080')
    server.serve_forever()
EOF

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

CMD ["python", "/app/server.py"]
