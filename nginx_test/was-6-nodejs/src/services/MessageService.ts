import { Kafka, Producer, logLevel } from 'kafkajs';
import { v4 as uuidv4 } from 'uuid';
import { logger } from '../logger';

interface HealthStatus {
  status: 'UP' | 'DOWN';
  topics_count?: number;
  error?: string;
}

export class MessageService {
  private kafka: Kafka;
  private producer: Producer;
  private isConnected = false;

  constructor() {
    const brokers = (process.env.KAFKA_BROKERS || 'kafka-1:9092,kafka-2:9092,kafka-3:9092').split(',');

    this.kafka = new Kafka({
      clientId: 'was-6-nodejs',
      brokers,
      logLevel: logLevel.WARN,
    });

    this.producer = this.kafka.producer();
  }

  async init(): Promise<void> {
    await this.producer.connect();
    this.isConnected = true;
    logger.info('Kafka producer connected');
  }

  async send(topic: string, key: string | undefined, message: unknown): Promise<string> {
    const messageId = uuidv4();
    const messageKey = key || messageId;

    const wrappedMessage = JSON.stringify({
      id: messageId,
      source: 'was-6-nodejs',
      timestamp: new Date().toISOString(),
      payload: message,
    });

    try {
      const result = await this.producer.send({
        topic,
        messages: [{
          key: messageKey,
          value: wrappedMessage,
          headers: {
            'content-type': 'application/json',
            'message-id': messageId,
          },
        }],
      });

      logger.info('Message sent', {
        messageId,
        topic,
        partition: result[0].partition,
        offset: result[0].offset,
      });

      return messageId;
    } catch (err) {
      logger.error('Failed to send message', { error: (err as Error).message });
      throw err;
    }
  }

  async healthCheck(): Promise<HealthStatus> {
    if (!this.isConnected) {
      return { status: 'DOWN', error: 'Not connected' };
    }

    try {
      const admin = this.kafka.admin();
      await admin.connect();
      const topics = await admin.listTopics();
      await admin.disconnect();
      return { status: 'UP', topics_count: topics.length };
    } catch (err) {
      return { status: 'DOWN', error: (err as Error).message };
    }
  }

  async disconnect(): Promise<void> {
    await this.producer.disconnect();
  }
}
