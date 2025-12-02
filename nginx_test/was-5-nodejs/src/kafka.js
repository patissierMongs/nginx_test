import { Kafka, logLevel } from 'kafkajs';
import { v4 as uuidv4 } from 'uuid';
import { logger } from './logger.js';

const SERVICE_NAME = process.env.OTEL_SERVICE_NAME || 'was-5-nodejs';
const KAFKA_BROKERS = (process.env.KAFKA_BROKERS || 'kafka-1:9092,kafka-2:9092,kafka-3:9092').split(',');

const kafka = new Kafka({
  clientId: SERVICE_NAME,
  brokers: KAFKA_BROKERS,
  logLevel: logLevel.WARN,
  retry: {
    initialRetryTime: 100,
    retries: 8
  }
});

export const kafkaProducer = kafka.producer({
  allowAutoTopicCreation: true,
  transactionTimeout: 30000
});

let isConnected = false;

export async function initKafka() {
  try {
    await kafkaProducer.connect();
    isConnected = true;
    logger.info('Kafka producer connected');
  } catch (err) {
    logger.error('Failed to connect Kafka producer', { error: err.message });
    throw err;
  }
}

export async function sendMessage(topic, key, message) {
  const messageId = uuidv4();
  const messageKey = key || messageId;

  const wrappedMessage = JSON.stringify({
    id: messageId,
    source: SERVICE_NAME,
    timestamp: new Date().toISOString(),
    payload: typeof message === 'string' ? JSON.parse(message) : message
  });

  try {
    const result = await kafkaProducer.send({
      topic,
      messages: [
        {
          key: messageKey,
          value: wrappedMessage,
          headers: {
            'content-type': 'application/json',
            'message-id': messageId,
            'source': SERVICE_NAME
          }
        }
      ]
    });

    logger.info('Message sent to Kafka', {
      messageId,
      topic,
      partition: result[0].partition,
      offset: result[0].offset
    });

    return messageId;
  } catch (err) {
    logger.error('Failed to send message to Kafka', {
      messageId,
      topic,
      error: err.message
    });
    throw err;
  }
}

export async function kafkaHealthCheck() {
  if (!isConnected) {
    return {
      status: 'DOWN',
      error: 'Producer not connected'
    };
  }

  try {
    const admin = kafka.admin();
    await admin.connect();
    const topics = await admin.listTopics();
    await admin.disconnect();

    return {
      status: 'UP',
      topics_count: topics.length
    };
  } catch (err) {
    return {
      status: 'DOWN',
      error: err.message
    };
  }
}
