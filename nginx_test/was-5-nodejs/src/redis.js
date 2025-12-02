import Redis from 'ioredis';
import { logger } from './logger.js';

const REDIS_NODES = process.env.REDIS_NODES || 'redis-1:6379,redis-2:6379,redis-3:6379';
const REDIS_PASSWORD = process.env.REDIS_PASSWORD || '';

// Parse Redis cluster nodes
const nodes = REDIS_NODES.split(',').map(node => {
  const [host, port] = node.split(':');
  return { host, port: parseInt(port) || 6379 };
});

// Create Redis cluster client
export const redisClient = new Redis.Cluster(nodes, {
  redisOptions: {
    password: REDIS_PASSWORD || undefined,
  },
  clusterRetryStrategy: (times) => {
    if (times > 10) {
      logger.error('Redis cluster connection failed after 10 retries');
      return null;
    }
    return Math.min(times * 100, 3000);
  },
  enableReadyCheck: true,
  scaleReads: 'slave',
});

redisClient.on('connect', () => {
  logger.info('Connected to Redis cluster');
});

redisClient.on('error', (err) => {
  logger.error('Redis cluster error', { error: err.message });
});

export async function getCache(key) {
  try {
    const value = await redisClient.get(key);
    logger.debug(`Cache GET: key=${key}, found=${value !== null}`);
    return value;
  } catch (err) {
    logger.error(`Cache GET error: key=${key}`, { error: err.message });
    return null;
  }
}

export async function setCache(key, value, ttlSeconds = 3600) {
  try {
    await redisClient.set(key, typeof value === 'object' ? JSON.stringify(value) : value, 'EX', ttlSeconds);
    logger.debug(`Cache SET: key=${key}, ttl=${ttlSeconds}`);
    return true;
  } catch (err) {
    logger.error(`Cache SET error: key=${key}`, { error: err.message });
    throw err;
  }
}

export async function deleteCache(key) {
  try {
    const result = await redisClient.del(key);
    logger.debug(`Cache DELETE: key=${key}, deleted=${result > 0}`);
    return result > 0;
  } catch (err) {
    logger.error(`Cache DELETE error: key=${key}`, { error: err.message });
    return false;
  }
}

export async function redisHealthCheck() {
  try {
    const result = await redisClient.ping();
    return {
      status: result === 'PONG' ? 'UP' : 'DOWN',
      response: result
    };
  } catch (err) {
    return {
      status: 'DOWN',
      error: err.message
    };
  }
}
