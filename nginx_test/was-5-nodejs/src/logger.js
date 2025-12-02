import winston from 'winston';
import LokiTransport from 'winston-loki';

const SERVICE_NAME = process.env.OTEL_SERVICE_NAME || 'was-5-nodejs';
const LOKI_URL = process.env.LOKI_URL || 'http://loki:3100';

const logFormat = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss.SSS' }),
  winston.format.errors({ stack: true }),
  winston.format.json()
);

const consoleFormat = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss.SSS' }),
  winston.format.colorize(),
  winston.format.printf(({ timestamp, level, message, ...meta }) => {
    const traceId = meta.traceId || '-';
    const spanId = meta.spanId || '-';
    return `${timestamp} [${traceId},${spanId}] ${level}: ${message} ${Object.keys(meta).length ? JSON.stringify(meta) : ''}`;
  })
);

const transports = [
  new winston.transports.Console({
    format: process.env.NODE_ENV === 'production' ? logFormat : consoleFormat,
  }),
];

// Add Loki transport in production
if (process.env.NODE_ENV === 'production') {
  transports.push(
    new LokiTransport({
      host: LOKI_URL,
      labels: { app: SERVICE_NAME, environment: process.env.ENVIRONMENT || 'development' },
      json: true,
      format: logFormat,
      replaceTimestamp: true,
      onConnectionError: (err) => console.error('Loki connection error:', err),
    })
  );
}

export const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  defaultMeta: { service: SERVICE_NAME },
  transports,
});

// Add trace context to logs
export function logWithTrace(level, message, traceId, spanId, meta = {}) {
  logger.log(level, message, { traceId, spanId, ...meta });
}
