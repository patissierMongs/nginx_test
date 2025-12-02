module github.com/nginx-test/was-8-go

go 1.22

require (
	github.com/gofiber/fiber/v2 v2.52.0
	github.com/gofiber/contrib/otelfiber v1.0.10
	github.com/prometheus/client_golang v1.18.0
	github.com/redis/go-redis/v9 v9.4.0
	github.com/segmentio/kafka-go v0.4.47
	github.com/google/uuid v1.5.0
	go.opentelemetry.io/otel v1.22.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace v1.22.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc v1.22.0
	go.opentelemetry.io/otel/sdk v1.22.0
	go.opentelemetry.io/otel/trace v1.22.0
	go.uber.org/zap v1.26.0
)
