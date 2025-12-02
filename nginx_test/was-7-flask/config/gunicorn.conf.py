# Gunicorn configuration for WAS-7 Flask
import multiprocessing
import os

# Server socket
bind = '0.0.0.0:5000'
backlog = 2048

# Worker processes
workers = int(os.getenv('GUNICORN_WORKERS', multiprocessing.cpu_count() * 2 + 1))
worker_class = 'gevent'
worker_connections = 1000
max_requests = 10000
max_requests_jitter = 1000
timeout = 30
graceful_timeout = 30
keepalive = 5

# Process naming
proc_name = 'was-7-flask'

# Logging
accesslog = '-'
errorlog = '-'
loglevel = os.getenv('LOG_LEVEL', 'info')
access_log_format = '%(h)s %(l)s %(u)s %(t)s "%(r)s" %(s)s %(b)s "%(f)s" "%(a)s" %(D)s'

# Server mechanics
daemon = False
pidfile = None
umask = 0
user = None
group = None
tmp_upload_dir = None

# SSL (if needed)
# keyfile = None
# certfile = None

# Hooks
def on_starting(server):
    print(f'Starting Gunicorn server with {workers} workers')

def on_reload(server):
    print('Reloading Gunicorn server')

def worker_int(worker):
    print(f'Worker {worker.pid} interrupted')

def worker_abort(worker):
    print(f'Worker {worker.pid} aborted')

def pre_fork(server, worker):
    pass

def post_fork(server, worker):
    print(f'Worker {worker.pid} spawned')

def post_worker_init(worker):
    print(f'Worker {worker.pid} initialized')

def worker_exit(server, worker):
    print(f'Worker {worker.pid} exited')

def nworkers_changed(server, new_value, old_value):
    print(f'Workers changed from {old_value} to {new_value}')

def on_exit(server):
    print('Shutting down Gunicorn server')
