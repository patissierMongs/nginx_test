import os
import json
import uuid
import logging
from datetime import datetime
from confluent_kafka import Producer
from confluent_kafka.admin import AdminClient

logger = logging.getLogger('was-7-flask')

SERVICE_NAME = os.getenv('OTEL_SERVICE_NAME', 'was-7-flask')
KAFKA_BROKERS = os.getenv('KAFKA_BROKERS', 'kafka-1:9092,kafka-2:9092,kafka-3:9092')


class MessageService:
    def __init__(self):
        try:
            self.producer = Producer({
                'bootstrap.servers': KAFKA_BROKERS,
                'client.id': SERVICE_NAME,
                'acks': 'all',
                'retries': 3,
                'retry.backoff.ms': 100
            })
            self.admin = AdminClient({
                'bootstrap.servers': KAFKA_BROKERS
            })
            logger.info('Kafka producer connected')
        except Exception as e:
            logger.error(f'Failed to connect to Kafka: {e}')
            self.producer = None
            self.admin = None

    def send(self, topic, key, message):
        if not self.producer:
            raise Exception('Kafka not connected')

        message_id = str(uuid.uuid4())
        message_key = key or message_id

        wrapped_message = json.dumps({
            'id': message_id,
            'source': SERVICE_NAME,
            'timestamp': datetime.utcnow().isoformat() + 'Z',
            'payload': message if isinstance(message, dict) else json.loads(message) if isinstance(message, str) and message.startswith('{') else message
        })

        def delivery_callback(err, msg):
            if err:
                logger.error(f'Message delivery failed: {err}')
            else:
                logger.info(f'Message delivered: id={message_id}, topic={topic}, partition={msg.partition()}, offset={msg.offset()}')

        try:
            self.producer.produce(
                topic,
                key=message_key,
                value=wrapped_message,
                headers={
                    'content-type': 'application/json',
                    'message-id': message_id,
                    'source': SERVICE_NAME
                },
                callback=delivery_callback
            )
            self.producer.poll(0)
            return message_id
        except Exception as e:
            logger.error(f'Failed to send message: {e}')
            raise

    def health_check(self):
        if not self.admin:
            return {'status': 'DOWN', 'error': 'Not connected'}
        try:
            metadata = self.admin.list_topics(timeout=5)
            return {'status': 'UP', 'topics_count': len(metadata.topics)}
        except Exception as e:
            return {'status': 'DOWN', 'error': str(e)}

    def flush(self):
        if self.producer:
            self.producer.flush()
