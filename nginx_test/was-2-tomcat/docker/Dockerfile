# Simplified WAS-2 Tomcat simulator
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
            response = {"status": "UP", "service": "was-2-tomcat", "type": "Tomcat (Legacy On-Premise)"}
            self.wfile.write(json.dumps(response).encode())
        elif self.path == '/' or self.path == '/info':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {
                "app": "was-2-tomcat",
                "server": "Apache Tomcat",
                "version": "9.0",
                "java": "11",
                "type": "Legacy On-Premise",
                "hostname": socket.gethostname()
            }
            self.wfile.write(json.dumps(response).encode())
        elif self.path.startswith('/api/'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"message": "Hello from WAS-2 Tomcat", "path": self.path}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        print(f"[WAS-2] {args[0]}")

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', 8080), Handler)
    print('WAS-2 Tomcat (Legacy On-Premise) started on port 8080')
    server.serve_forever()
EOF

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

CMD ["python", "/app/server.py"]
