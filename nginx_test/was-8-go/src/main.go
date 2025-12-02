package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"os/signal"
	"runtime"
	"syscall"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/compress"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/helmet"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	"github.com/gofiber/fiber/v2/middleware/requestid"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.uber.org/zap"
	"net/http"

	"github.com/nginx-test/was-8-go/internal/cache"
	"github.com/nginx-test/was-8-go/internal/messaging"
	"github.com/nginx-test/was-8-go/internal/tracing"
)

const serviceName = "was-8-go"

var (
	httpRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "http_requests_total",
			Help: "Total number of HTTP requests",
		},
		[]string{"method", "path", "status"},
	)

	httpRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_request_duration_seconds",
			Help:    "HTTP request duration in seconds",
			Buckets: []float64{0.01, 0.05, 0.1, 0.5, 1, 2, 5},
		},
		[]string{"method", "path"},
	)
)

func init() {
	prometheus.MustRegister(httpRequestsTotal)
	prometheus.MustRegister(httpRequestDuration)
}

func main() {
	// Initialize logger
	zapLogger, _ := zap.NewProduction()
	defer zapLogger.Sync()
	sugar := zapLogger.Sugar()

	// Initialize tracing
	tp, err := tracing.InitTracer(serviceName)
	if err != nil {
		sugar.Warnf("Failed to initialize tracing: %v", err)
	} else {
		defer tp.Shutdown(context.Background())
	}

	// Initialize services
	cacheService := cache.NewCacheService()
	messageService := messaging.NewMessageService()

	// Create Fiber app
	app := fiber.New(fiber.Config{
		AppName:               serviceName,
		DisableStartupMessage: false,
		ReadTimeout:           30 * time.Second,
		WriteTimeout:          30 * time.Second,
		IdleTimeout:           120 * time.Second,
	})

	// Middleware
	app.Use(recover.New())
	app.Use(helmet.New())
	app.Use(cors.New())
	app.Use(compress.New())
	app.Use(requestid.New())
	app.Use(logger.New(logger.Config{
		Format:     "${time} | ${status} | ${latency} | ${ip} | ${method} | ${path} | ${error}\n",
		TimeFormat: "2006-01-02 15:04:05",
	}))

	// Metrics middleware
	app.Use(func(c *fiber.Ctx) error {
		start := time.Now()
		err := c.Next()
		duration := time.Since(start).Seconds()

		status := c.Response().StatusCode()
		httpRequestsTotal.WithLabelValues(c.Method(), c.Path(), fmt.Sprintf("%d", status)).Inc()
		httpRequestDuration.WithLabelValues(c.Method(), c.Path()).Observe(duration)

		return err
	})

	// Health check
	app.Get("/health", func(c *fiber.Ctx) error {
		redisStatus := cacheService.HealthCheck()
		kafkaStatus := messageService.HealthCheck()

		return c.JSON(fiber.Map{
			"status":    "UP",
			"service":   serviceName,
			"timestamp": time.Now().UTC().Format(time.RFC3339),
			"checks": fiber.Map{
				"redis": redisStatus,
				"kafka": kafkaStatus,
			},
		})
	})

	// Metrics endpoint
	app.Get("/metrics", func(c *fiber.Ctx) error {
		handler := promhttp.Handler()
		handler.ServeHTTP(c.Response().BodyWriter().(http.ResponseWriter), &http.Request{})
		return nil
	})

	// API routes
	api := app.Group("/api")

	api.Get("/info", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"service":   serviceName,
			"type":      "kubernetes-containerd-runtime",
			"framework": "Go 1.22 + Fiber",
			"timestamp": time.Now().UTC().Format(time.RFC3339),
			"hostname":  getHostname(),
			"ip":        getLocalIP(),
			"goVersion": runtime.Version(),
			"environment": fiber.Map{
				"POD_NAME":      getEnv("POD_NAME", "local"),
				"POD_NAMESPACE": getEnv("POD_NAMESPACE", "default"),
				"NODE_NAME":     getEnv("NODE_NAME", "local"),
			},
		})
	})

	// Cache endpoints
	api.Get("/cache/:key", func(c *fiber.Ctx) error {
		key := c.Params("key")
		value, found := cacheService.Get(key)

		return c.JSON(fiber.Map{
			"operation": "GET",
			"key":       key,
			"value":     value,
			"found":     found,
			"source":    "redis-cluster",
		})
	})

	api.Put("/cache/:key", func(c *fiber.Ctx) error {
		key := c.Params("key")

		var body struct {
			Value interface{} `json:"value"`
			TTL   int         `json:"ttl"`
		}
		if err := c.BodyParser(&body); err != nil {
			return c.Status(400).JSON(fiber.Map{"error": err.Error()})
		}

		ttl := body.TTL
		if ttl == 0 {
			ttl = 3600
		}

		valueStr, _ := json.Marshal(body.Value)
		if err := cacheService.Set(key, string(valueStr), time.Duration(ttl)*time.Second); err != nil {
			return c.Status(500).JSON(fiber.Map{"error": err.Error()})
		}

		return c.JSON(fiber.Map{
			"operation":   "SET",
			"key":         key,
			"value":       body.Value,
			"ttl":         ttl,
			"success":     true,
			"destination": "redis-cluster",
		})
	})

	api.Delete("/cache/:key", func(c *fiber.Ctx) error {
		key := c.Params("key")
		deleted := cacheService.Delete(key)

		return c.JSON(fiber.Map{
			"operation":   "DELETE",
			"key":         key,
			"deleted":     deleted,
			"destination": "redis-cluster",
		})
	})

	// Message endpoint
	api.Post("/message", func(c *fiber.Ctx) error {
		var body struct {
			Topic   string      `json:"topic"`
			Key     string      `json:"key"`
			Message interface{} `json:"message"`
		}
		if err := c.BodyParser(&body); err != nil {
			return c.Status(400).JSON(fiber.Map{"error": err.Error()})
		}

		topic := body.Topic
		if topic == "" {
			topic = "nginx-test-events"
		}

		messageID, err := messageService.Send(topic, body.Key, body.Message)
		if err != nil {
			return c.Status(500).JSON(fiber.Map{"error": err.Error()})
		}

		return c.JSON(fiber.Map{
			"operation": "PUBLISH",
			"topic":     topic,
			"key":       body.Key,
			"messageId": messageID,
			"success":   true,
			"broker":    "kafka-cluster",
		})
	})

	// Slow endpoint
	api.Get("/slow", func(c *fiber.Ctx) error {
		delay := c.QueryInt("delay", 1000)
		time.Sleep(time.Duration(delay) * time.Millisecond)

		return c.JSON(fiber.Map{
			"service":  serviceName,
			"endpoint": "/api/slow",
			"delay_ms": delay,
			"message":  "This endpoint simulates slow responses",
		})
	})

	// Error endpoint
	api.Get("/error", func(c *fiber.Ctx) error {
		code := c.QueryInt("code", 500)
		sugar.Errorf("Error endpoint called with code: %d", code)

		return c.Status(code).JSON(fiber.Map{
			"service":    serviceName,
			"endpoint":   "/api/error",
			"error_code": code,
			"message":    "This endpoint simulates errors",
		})
	})

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-quit
		sugar.Info("Shutting down server...")
		cacheService.Close()
		messageService.Close()
		app.Shutdown()
	}()

	// Start server
	port := getEnv("PORT", "8000")
	sugar.Infof("Starting %s on port %s", serviceName, port)
	if err := app.Listen(":" + port); err != nil {
		sugar.Fatalf("Failed to start server: %v", err)
	}
}

func getHostname() string {
	hostname, err := os.Hostname()
	if err != nil {
		return "unknown"
	}
	return hostname
}

func getLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "127.0.0.1"
	}
	for _, addr := range addrs {
		if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ipnet.IP.To4() != nil {
				return ipnet.IP.String()
			}
		}
	}
	return "127.0.0.1"
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
