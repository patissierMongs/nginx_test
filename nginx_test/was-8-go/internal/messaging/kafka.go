package messaging

import (
	"context"
	"encoding/json"
	"os"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/segmentio/kafka-go"
	"go.uber.org/zap"
)

const serviceName = "was-8-go"

type MessageService struct {
	writer *kafka.Writer
	reader *kafka.Reader
	logger *zap.SugaredLogger
}

type WrappedMessage struct {
	ID        string      `json:"id"`
	Source    string      `json:"source"`
	Timestamp string      `json:"timestamp"`
	Payload   interface{} `json:"payload"`
}

func NewMessageService() *MessageService {
	zapLogger, _ := zap.NewProduction()
	sugar := zapLogger.Sugar()

	brokers := os.Getenv("KAFKA_BROKERS")
	if brokers == "" {
		brokers = "kafka-1:9092,kafka-2:9092,kafka-3:9092"
	}

	brokerList := strings.Split(brokers, ",")

	writer := &kafka.Writer{
		Addr:         kafka.TCP(brokerList...),
		Balancer:     &kafka.LeastBytes{},
		BatchSize:    100,
		BatchTimeout: 10 * time.Millisecond,
		RequiredAcks: kafka.RequireAll,
		Async:        false,
	}

	sugar.Infof("Kafka writer initialized with brokers: %v", brokerList)

	return &MessageService{
		writer: writer,
		logger: sugar,
	}
}

func (s *MessageService) Send(topic, key string, message interface{}) (string, error) {
	messageID := uuid.New().String()
	messageKey := key
	if messageKey == "" {
		messageKey = messageID
	}

	wrapped := WrappedMessage{
		ID:        messageID,
		Source:    serviceName,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
		Payload:   message,
	}

	value, err := json.Marshal(wrapped)
	if err != nil {
		s.logger.Errorf("Failed to marshal message: %v", err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	err = s.writer.WriteMessages(ctx, kafka.Message{
		Topic: topic,
		Key:   []byte(messageKey),
		Value: value,
		Headers: []kafka.Header{
			{Key: "content-type", Value: []byte("application/json")},
			{Key: "message-id", Value: []byte(messageID)},
			{Key: "source", Value: []byte(serviceName)},
		},
	})

	if err != nil {
		s.logger.Errorf("Failed to send message: id=%s, topic=%s, error=%v", messageID, topic, err)
		return "", err
	}

	s.logger.Infof("Message sent: id=%s, topic=%s", messageID, topic)
	return messageID, nil
}

func (s *MessageService) HealthCheck() map[string]interface{} {
	brokers := os.Getenv("KAFKA_BROKERS")
	if brokers == "" {
		brokers = "kafka-1:9092,kafka-2:9092,kafka-3:9092"
	}

	brokerList := strings.Split(brokers, ",")

	conn, err := kafka.Dial("tcp", brokerList[0])
	if err != nil {
		return map[string]interface{}{
			"status": "DOWN",
			"error":  err.Error(),
		}
	}
	defer conn.Close()

	partitions, err := conn.ReadPartitions()
	if err != nil {
		return map[string]interface{}{
			"status": "DOWN",
			"error":  err.Error(),
		}
	}

	topics := make(map[string]bool)
	for _, p := range partitions {
		topics[p.Topic] = true
	}

	return map[string]interface{}{
		"status":       "UP",
		"topics_count": len(topics),
	}
}

func (s *MessageService) Close() {
	if s.writer != nil {
		s.writer.Close()
	}
}
