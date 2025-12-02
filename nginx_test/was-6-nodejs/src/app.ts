import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import { register as metricsRegistry, collectDefaultMetrics, Counter, Histogram } from 'prom-client';
import { v4 as uuidv4 } from 'uuid';
import os from 'os';

import { initTracing } from './tracing';
import { logger } from './logger';
import { CacheService } from './services/CacheService';
import { MessageService } from './services/MessageService';

const SERVICE_NAME = 'was-6-nodejs';
const PORT = process.env.PORT || 3000;

// Extend Express Request
declare global {
  namespace Express {
    interface Request {
      requestId: string;
    }
  }
}

// Custom metrics
collectDefaultMetrics({ register: metricsRegistry });

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

async function bootstrap(): Promise<void> {
  // Initialize OpenTelemetry
  await initTracing();

  const app = express();
  const cacheService = new CacheService();
  const messageService = new MessageService();

  // Middleware
  app.use(helmet());
  app.use(cors());
  app.use(compression());
  app.use(express.json());

  // Request logging middleware
  app.use((req: Request, res: Response, next: NextFunction) => {
    const start = Date.now();
    const requestId = (req.headers['x-request-id'] as string) || uuidv4();
    req.requestId = requestId;
    res.setHeader('X-Request-Id', requestId);

    res.on('finish', () => {
      const duration = (Date.now() - start) / 1000;
      const path = req.route?.path || req.path;
      httpRequestsTotal.inc({ method: req.method, path, status: res.statusCode });
      httpRequestDuration.observe({ method: req.method, path, status: res.statusCode }, duration);

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

  // Health check
  app.get('/health', async (_req: Request, res: Response) => {
    const redisStatus = await cacheService.healthCheck();
    const kafkaStatus = await messageService.healthCheck();

    res.json({
      status: 'UP',
      service: SERVICE_NAME,
      timestamp: new Date().toISOString(),
      checks: { redis: redisStatus, kafka: kafkaStatus }
    });
  });

  // Metrics
  app.get('/metrics', async (_req: Request, res: Response) => {
    res.set('Content-Type', metricsRegistry.contentType);
    res.send(await metricsRegistry.metrics());
  });

  // API Info
  app.get('/api/info', (_req: Request, res: Response) => {
    res.json({
      service: SERVICE_NAME,
      type: 'kubernetes-containerd-runtime',
      framework: 'Node.js 20 LTS + Express + TypeScript',
      timestamp: new Date().toISOString(),
      hostname: os.hostname(),
      ip: getLocalIP(),
      nodeVersion: process.version,
      environment: {
        POD_NAME: process.env.POD_NAME || 'local',
        POD_NAMESPACE: process.env.POD_NAMESPACE || 'default',
        NODE_NAME: process.env.NODE_NAME || 'local'
      }
    });
  });

  // Cache GET
  app.get('/api/cache/:key', async (req: Request, res: Response) => {
    const { key } = req.params;
    const value = await cacheService.get(key);
    res.json({
      operation: 'GET',
      key,
      value,
      found: value !== null,
      source: 'redis-cluster'
    });
  });

  // Cache SET
  app.put('/api/cache/:key', async (req: Request, res: Response) => {
    const { key } = req.params;
    const { value, ttl = 3600 } = req.body;
    await cacheService.set(key, value, ttl);
    res.json({
      operation: 'SET',
      key,
      value,
      ttl,
      success: true,
      destination: 'redis-cluster'
    });
  });

  // Cache DELETE
  app.delete('/api/cache/:key', async (req: Request, res: Response) => {
    const { key } = req.params;
    const deleted = await cacheService.delete(key);
    res.json({
      operation: 'DELETE',
      key,
      deleted,
      destination: 'redis-cluster'
    });
  });

  // Message
  app.post('/api/message', async (req: Request, res: Response) => {
    const { topic = 'nginx-test-events', message, key } = req.body;
    const messageId = await messageService.send(topic, key, message);
    res.json({
      operation: 'PUBLISH',
      topic,
      key: key || 'null',
      messageId,
      success: true,
      broker: 'kafka-cluster'
    });
  });

  // Slow endpoint
  app.get('/api/slow', async (req: Request, res: Response) => {
    const delay = parseInt(req.query.delay as string) || 1000;
    await new Promise(resolve => setTimeout(resolve, delay));
    res.json({
      service: SERVICE_NAME,
      endpoint: '/api/slow',
      delay_ms: delay,
      message: 'This endpoint simulates slow responses'
    });
  });

  // Error endpoint
  app.get('/api/error', (req: Request, res: Response) => {
    const code = parseInt(req.query.code as string) || 500;
    res.status(code).json({
      service: SERVICE_NAME,
      endpoint: '/api/error',
      error_code: code,
      message: 'This endpoint simulates errors'
    });
  });

  // Error handler
  app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
    logger.error('Unhandled error', { error: err.message, stack: err.stack });
    res.status(500).json({ error: 'Internal Server Error', message: err.message });
  });

  // Initialize services
  try {
    await messageService.init();
  } catch (err) {
    logger.warn('Failed to connect Kafka', { error: (err as Error).message });
  }

  app.listen(PORT, () => {
    logger.info(`${SERVICE_NAME} listening on port ${PORT}`);
  });

  // Graceful shutdown
  process.on('SIGTERM', async () => {
    logger.info('SIGTERM received');
    await cacheService.disconnect();
    await messageService.disconnect();
    process.exit(0);
  });
}

function getLocalIP(): string {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

bootstrap().catch(err => {
  console.error('Failed to start application', err);
  process.exit(1);
});
