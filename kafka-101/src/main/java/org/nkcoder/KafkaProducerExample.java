package org.nkcoder;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaProducerExample {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerExample.class);

    public static void main(String[] args) {

        logger.info("Starting Kafka Producer...");

        Properties props = KafkaProperties.getProducerProperties();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {

            // Send 10 messages
            for (int i = 0; i < 20; i++) {
                String key = "key-" + (i % 3);
                String value = "message-" + i + "-" + System.currentTimeMillis();

                ProducerRecord<String, String> record = new ProducerRecord<>(KafkaProperties.TOPIC_1, key, value);

                try {
                    RecordMetadata metadata = producer.send(record).get();
                    logger.info(
                            "Sent record(key={} value={}) to partition {} with offset {}",
                            key,
                            value,
                            metadata.partition(),
                            metadata.offset());
                } catch (ExecutionException | InterruptedException e) {
                    logger.error("Error sending message: {}", e.getMessage(), e);
                }

                // Sleep a bit between messages
                Thread.sleep(500);
            }

            logger.info("Producer finished sending messages.");
        } catch (Exception e) {
            logger.error("Error in producer: {}", e.getMessage(), e);
        }
    }
}
