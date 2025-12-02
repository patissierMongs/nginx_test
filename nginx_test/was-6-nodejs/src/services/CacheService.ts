import Redis from 'ioredis';
import { logger } from '../logger';

interface HealthStatus {
  status: 'UP' | 'DOWN';
  response?: string;
  error?: string;
}

export class CacheService {
  private client: Redis.Cluster;

  constructor() {
    const nodes = (process.env.REDIS_NODES || 'redis-1:6379,redis-2:6379,redis-3:6379')
      .split(',')
      .map(node => {
        const [host, port] = node.split(':');
        return { host, port: parseInt(port) || 6379 };
      });

    this.client = new Redis.Cluster(nodes, {
      redisOptions: {
        password: process.env.REDIS_PASSWORD || undefined,
      },
      clusterRetryStrategy: (times: number) => {
        if (times > 10) return null;
        return Math.min(times * 100, 3000);
      },
    });

    this.client.on('connect', () => logger.info('Redis connected'));
    this.client.on('error', (err) => logger.error('Redis error', { error: err.message }));
  }

  async get(key: string): Promise<string | null> {
    try {
      return await this.client.get(key);
    } catch (err) {
      logger.error(`Cache GET error: ${key}`, { error: (err as Error).message });
      return null;
    }
  }

  async set(key: string, value: unknown, ttlSeconds = 3600): Promise<boolean> {
    try {
      const strValue = typeof value === 'object' ? JSON.stringify(value) : String(value);
      await this.client.set(key, strValue, 'EX', ttlSeconds);
      return true;
    } catch (err) {
      logger.error(`Cache SET error: ${key}`, { error: (err as Error).message });
      throw err;
    }
  }

  async delete(key: string): Promise<boolean> {
    try {
      const result = await this.client.del(key);
      return result > 0;
    } catch (err) {
      logger.error(`Cache DELETE error: ${key}`, { error: (err as Error).message });
      return false;
    }
  }

  async healthCheck(): Promise<HealthStatus> {
    try {
      const result = await this.client.ping();
      return { status: result === 'PONG' ? 'UP' : 'DOWN', response: result };
    } catch (err) {
      return { status: 'DOWN', error: (err as Error).message };
    }
  }

  async disconnect(): Promise<void> {
    await this.client.quit();
  }
}
