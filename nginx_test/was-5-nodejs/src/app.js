import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import { register as metricsRegistry, collectDefaultMetrics, Counter, Histogram } from 'prom-client';
import { v4 as uuidv4 } from 'uuid';
import os from 'os';

import { initTracing } from './tracing.js';
import { logger } from './logger.js';
import { redisClient, getCache, setCache, deleteCache, redisHealthCheck } from './redis.js';
import { kafkaProducer, sendMessage, kafkaHealthCheck, initKafka } from './kafka.js';

// Initialize OpenTelemetry
await initTracing();

const app = express();
const PORT = process.env.PORT || 3000;
const SERVICE_NAME = 'was-5-nodejs';

// Collect default metrics
collectDefaultMetrics({ register: metricsRegistry });

// Custom metrics
const httpRequestsTotal = new Counter({
  name: 'http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'path', 'status'],
  registers: [metricsRegistry]
});

const httpRequestDuration = new Histogram({
  name: 'http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'path', 'status'],
  buckets: [0.01, 0.05, 0.1, 0.5, 1, 2, 5],
  registers: [metricsRegistry]
});

// Middleware
app.use(helmet());
app.use(cors());
app.use(compression());
app.use(express.json());

// Request logging and metrics middleware
app.use((req, res, next) => {
  const start = Date.now();
  const requestId = req.headers['x-request-id'] || uuidv4();
  req.requestId = requestId;
  res.setHeader('X-Request-Id', requestId);

  res.on('finish', () => {
    const duration = (Date.now() - start) / 1000;
    httpRequestsTotal.inc({ method: req.method, path: req.route?.path || req.path, status: res.statusCode });
    httpRequestDuration.observe({ method: req.method, path: req.route?.path || req.path, status: res.statusCode }, duration);

    logger.info('Request completed', {
      requestId,
      method: req.method,
      path: req.path,
      status: res.statusCode,
      duration: `${duration}s`
    });
  });

  next();
});

// Health check endpoint
app.get('/health', async (req, res) => {
  const redisStatus = await redisHealthCheck();
  const kafkaStatus = await kafkaHealthCheck();

  const health = {
    status: 'UP',
    service: SERVICE_NAME,
    timestamp: new Date().toISOString(),
    checks: {
      redis: redisStatus,
      kafka: kafkaStatus
    }
  };

  res.json(health);
});

// Metrics endpoint for Prometheus
app.get('/metrics', async (req, res) => {
  res.set('Content-Type', metricsRegistry.contentType);
  res.send(await metricsRegistry.metrics());
});

// API endpoints
app.get('/api/info', (req, res) => {
  const info = {
    service: SERVICE_NAME,
    type: 'kubernetes-containerd-runtime',
    framework: 'Node.js 20 LTS + Express',
    timestamp: new Date().toISOString(),
    hostname: os.hostname(),
    ip: getLocalIP(),
    nodeVersion: process.version,
    environment: {
      POD_NAME: process.env.POD_NAME || 'local',
      POD_NAMESPACE: process.env.POD_NAMESPACE || 'default',
      NODE_NAME: process.env.NODE_NAME || 'local'
    }
  };

  res.json(info);
});

// Cache endpoints
app.get('/api/cache/:key', async (req, res) => {
  const { key } = req.params;
  logger.info(`Cache GET request for key: ${key}`);

  const value = await getCache(key);

  res.json({
    operation: 'GET',
    key,
    value,
    found: value !== null,
    source: 'redis-cluster'
  });
});

app.put('/api/cache/:key', async (req, res) => {
  const { key } = req.params;
  const { value, ttl = 3600 } = req.body;
  logger.info(`Cache SET request for key: ${key}`);

  await setCache(key, value, ttl);

  res.json({
    operation: 'SET',
    key,
    value,
    ttl,
    success: true,
    destination: 'redis-cluster'
  });
});

app.delete('/api/cache/:key', async (req, res) => {
  const { key } = req.params;
  logger.info(`Cache DELETE request for key: ${key}`);

  const deleted = await deleteCache(key);

  res.json({
    operation: 'DELETE',
    key,
    deleted,
    destination: 'redis-cluster'
  });
});

// Message endpoint
app.post('/api/message', async (req, res) => {
  const { topic = 'nginx-test-events', message, key } = req.body;
  logger.info(`Sending message to Kafka topic: ${topic}`);

  const messageId = await sendMessage(topic, key, message);

  res.json({
    operation: 'PUBLISH',
    topic,
    key: key || 'null',
    messageId,
    success: true,
    broker: 'kafka-cluster'
  });
});

// Slow endpoint for timeout testing
app.get('/api/slow', async (req, res) => {
  const delay = parseInt(req.query.delay) || 1000;
  logger.info(`Slow endpoint called with delay: ${delay}ms`);

  await new Promise(resolve => setTimeout(resolve, delay));

  res.json({
    service: SERVICE_NAME,
    endpoint: '/api/slow',
    delay_ms: delay,
    message: 'This endpoint simulates slow responses for testing timeouts'
  });
});

// Error endpoint for error handling testing
app.get('/api/error', (req, res) => {
  const code = parseInt(req.query.code) || 500;
  logger.error(`Error endpoint called with code: ${code}`);

  res.status(code).json({
    service: SERVICE_NAME,
    endpoint: '/api/error',
    error_code: code,
    message: 'This endpoint simulates errors for testing error handling'
  });
});

// Error handler
app.use((err, req, res, next) => {
  logger.error('Unhandled error', { error: err.message, stack: err.stack });
  res.status(500).json({ error: 'Internal Server Error', message: err.message });
});

// Helper function
function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

// Initialize and start server
async function start() {
  try {
    await initKafka();
    logger.info('Kafka producer connected');
  } catch (err) {
    logger.warn('Failed to connect to Kafka, continuing without it', { error: err.message });
  }

  app.listen(PORT, () => {
    logger.info(`${SERVICE_NAME} listening on port ${PORT}`);
  });
}

// Graceful shutdown
process.on('SIGTERM', async () => {
  logger.info('SIGTERM received, shutting down gracefully');
  await redisClient.quit();
  await kafkaProducer.disconnect();
  process.exit(0);
});

start();
