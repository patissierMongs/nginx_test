import os
import socket
import time
import uuid
import json
import logging
from datetime import datetime
from functools import wraps

from flask import Flask, request, jsonify, g
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST

from tracing import init_tracing
from cache import CacheService
from messaging import MessageService

# Initialize OpenTelemetry
init_tracing()

# Configure logging
from pythonjsonlogger import jsonlogger

logger = logging.getLogger('was-7-flask')
logger.setLevel(logging.INFO)

handler = logging.StreamHandler()
formatter = jsonlogger.JsonFormatter(
    '%(asctime)s %(levelname)s %(name)s %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
handler.setFormatter(formatter)
logger.addHandler(handler)

# Flask app
app = Flask(__name__)

SERVICE_NAME = 'was-7-flask'

# Prometheus metrics
REQUEST_COUNT = Counter(
    'http_requests_total',
    'Total HTTP requests',
    ['method', 'path', 'status']
)

REQUEST_LATENCY = Histogram(
    'http_request_duration_seconds',
    'HTTP request duration in seconds',
    ['method', 'path'],
    buckets=[0.01, 0.05, 0.1, 0.5, 1, 2, 5]
)

# Services
cache_service = CacheService()
message_service = MessageService()


def metrics_middleware(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        g.start_time = time.time()
        g.request_id = request.headers.get('X-Request-Id', str(uuid.uuid4()))

        response = f(*args, **kwargs)

        duration = time.time() - g.start_time
        status = response[1] if isinstance(response, tuple) else 200
        path = request.path

        REQUEST_COUNT.labels(
            method=request.method,
            path=path,
            status=status
        ).inc()

        REQUEST_LATENCY.labels(
            method=request.method,
            path=path
        ).observe(duration)

        logger.info('Request completed', extra={
            'request_id': g.request_id,
            'method': request.method,
            'path': path,
            'status': status,
            'duration': f'{duration:.3f}s'
        })

        return response
    return decorated_function


@app.before_request
def before_request():
    g.start_time = time.time()
    g.request_id = request.headers.get('X-Request-Id', str(uuid.uuid4()))


@app.after_request
def after_request(response):
    response.headers['X-Request-Id'] = g.request_id
    response.headers['X-Service'] = SERVICE_NAME
    return response


@app.route('/health')
def health():
    redis_status = cache_service.health_check()
    kafka_status = message_service.health_check()

    return jsonify({
        'status': 'UP',
        'service': SERVICE_NAME,
        'timestamp': datetime.utcnow().isoformat() + 'Z',
        'checks': {
            'redis': redis_status,
            'kafka': kafka_status
        }
    })


@app.route('/metrics')
def metrics():
    return generate_latest(), 200, {'Content-Type': CONTENT_TYPE_LATEST}


@app.route('/api/info')
@metrics_middleware
def info():
    return jsonify({
        'service': SERVICE_NAME,
        'type': 'kubernetes-docker-runtime',
        'framework': 'Flask 3.0 + Gunicorn',
        'timestamp': datetime.utcnow().isoformat() + 'Z',
        'hostname': socket.gethostname(),
        'ip': get_local_ip(),
        'pythonVersion': os.sys.version,
        'environment': {
            'POD_NAME': os.getenv('POD_NAME', 'local'),
            'POD_NAMESPACE': os.getenv('POD_NAMESPACE', 'default'),
            'NODE_NAME': os.getenv('NODE_NAME', 'local')
        }
    })


@app.route('/api/cache/<key>', methods=['GET'])
@metrics_middleware
def get_cache(key):
    value = cache_service.get(key)
    return jsonify({
        'operation': 'GET',
        'key': key,
        'value': value,
        'found': value is not None,
        'source': 'redis-cluster'
    })


@app.route('/api/cache/<key>', methods=['PUT'])
@metrics_middleware
def set_cache(key):
    data = request.get_json()
    value = data.get('value', '')
    ttl = data.get('ttl', 3600)

    cache_service.set(key, value, ttl)

    return jsonify({
        'operation': 'SET',
        'key': key,
        'value': value,
        'ttl': ttl,
        'success': True,
        'destination': 'redis-cluster'
    })


@app.route('/api/cache/<key>', methods=['DELETE'])
@metrics_middleware
def delete_cache(key):
    deleted = cache_service.delete(key)
    return jsonify({
        'operation': 'DELETE',
        'key': key,
        'deleted': deleted,
        'destination': 'redis-cluster'
    })


@app.route('/api/message', methods=['POST'])
@metrics_middleware
def send_message():
    data = request.get_json()
    topic = data.get('topic', 'nginx-test-events')
    message = data.get('message', '')
    key = data.get('key')

    message_id = message_service.send(topic, key, message)

    return jsonify({
        'operation': 'PUBLISH',
        'topic': topic,
        'key': key or 'null',
        'messageId': message_id,
        'success': True,
        'broker': 'kafka-cluster'
    })


@app.route('/api/slow')
@metrics_middleware
def slow():
    delay = int(request.args.get('delay', 1000))
    time.sleep(delay / 1000)

    return jsonify({
        'service': SERVICE_NAME,
        'endpoint': '/api/slow',
        'delay_ms': delay,
        'message': 'This endpoint simulates slow responses'
    })


@app.route('/api/error')
@metrics_middleware
def error():
    code = int(request.args.get('code', 500))
    logger.error(f'Error endpoint called with code: {code}')

    return jsonify({
        'service': SERVICE_NAME,
        'endpoint': '/api/error',
        'error_code': code,
        'message': 'This endpoint simulates errors'
    }), code


def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return '127.0.0.1'


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
