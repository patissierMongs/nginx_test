import os
import json
import logging
from redis.cluster import RedisCluster

logger = logging.getLogger('was-7-flask')

REDIS_NODES = os.getenv('REDIS_NODES', 'redis-1:6379,redis-2:6379,redis-3:6379')
REDIS_PASSWORD = os.getenv('REDIS_PASSWORD', '')


class CacheService:
    def __init__(self):
        nodes = []
        for node in REDIS_NODES.split(','):
            host, port = node.split(':')
            nodes.append({'host': host, 'port': int(port)})

        try:
            self.client = RedisCluster(
                startup_nodes=nodes,
                decode_responses=True,
                password=REDIS_PASSWORD or None,
                skip_full_coverage_check=True
            )
            logger.info('Redis cluster connected')
        except Exception as e:
            logger.error(f'Failed to connect to Redis: {e}')
            self.client = None

    def get(self, key):
        if not self.client:
            return None
        try:
            value = self.client.get(key)
            logger.debug(f'Cache GET: key={key}, found={value is not None}')
            return value
        except Exception as e:
            logger.error(f'Cache GET error: key={key}, error={e}')
            return None

    def set(self, key, value, ttl=3600):
        if not self.client:
            raise Exception('Redis not connected')
        try:
            str_value = json.dumps(value) if isinstance(value, (dict, list)) else str(value)
            self.client.set(key, str_value, ex=ttl)
            logger.debug(f'Cache SET: key={key}, ttl={ttl}')
            return True
        except Exception as e:
            logger.error(f'Cache SET error: key={key}, error={e}')
            raise

    def delete(self, key):
        if not self.client:
            return False
        try:
            result = self.client.delete(key)
            logger.debug(f'Cache DELETE: key={key}, deleted={result > 0}')
            return result > 0
        except Exception as e:
            logger.error(f'Cache DELETE error: key={key}, error={e}')
            return False

    def health_check(self):
        if not self.client:
            return {'status': 'DOWN', 'error': 'Not connected'}
        try:
            result = self.client.ping()
            return {'status': 'UP' if result else 'DOWN', 'response': str(result)}
        except Exception as e:
            return {'status': 'DOWN', 'error': str(e)}
