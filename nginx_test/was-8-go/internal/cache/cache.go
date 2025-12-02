package cache

import (
	"context"
	"os"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

type CacheService struct {
	client *redis.ClusterClient
	logger *zap.SugaredLogger
}

func NewCacheService() *CacheService {
	zapLogger, _ := zap.NewProduction()
	sugar := zapLogger.Sugar()

	nodes := os.Getenv("REDIS_NODES")
	if nodes == "" {
		nodes = "redis-1:6379,redis-2:6379,redis-3:6379"
	}

	addrs := strings.Split(nodes, ",")

	client := redis.NewClusterClient(&redis.ClusterOptions{
		Addrs:        addrs,
		Password:     os.Getenv("REDIS_PASSWORD"),
		DialTimeout:  5 * time.Second,
		ReadTimeout:  3 * time.Second,
		WriteTimeout: 3 * time.Second,
		PoolSize:     50,
		MinIdleConns: 10,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := client.Ping(ctx).Err(); err != nil {
		sugar.Warnf("Failed to connect to Redis cluster: %v", err)
	} else {
		sugar.Info("Connected to Redis cluster")
	}

	return &CacheService{
		client: client,
		logger: sugar,
	}
}

func (s *CacheService) Get(key string) (string, bool) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	value, err := s.client.Get(ctx, key).Result()
	if err == redis.Nil {
		return "", false
	}
	if err != nil {
		s.logger.Errorf("Cache GET error: key=%s, error=%v", key, err)
		return "", false
	}

	s.logger.Debugf("Cache GET: key=%s, found=true", key)
	return value, true
}

func (s *CacheService) Set(key, value string, ttl time.Duration) error {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	err := s.client.Set(ctx, key, value, ttl).Err()
	if err != nil {
		s.logger.Errorf("Cache SET error: key=%s, error=%v", key, err)
		return err
	}

	s.logger.Debugf("Cache SET: key=%s, ttl=%v", key, ttl)
	return nil
}

func (s *CacheService) Delete(key string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	result, err := s.client.Del(ctx, key).Result()
	if err != nil {
		s.logger.Errorf("Cache DELETE error: key=%s, error=%v", key, err)
		return false
	}

	s.logger.Debugf("Cache DELETE: key=%s, deleted=%v", key, result > 0)
	return result > 0
}

func (s *CacheService) HealthCheck() map[string]interface{} {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	result, err := s.client.Ping(ctx).Result()
	if err != nil {
		return map[string]interface{}{
			"status": "DOWN",
			"error":  err.Error(),
		}
	}

	return map[string]interface{}{
		"status":   "UP",
		"response": result,
	}
}

func (s *CacheService) Close() {
	if s.client != nil {
		s.client.Close()
	}
}
