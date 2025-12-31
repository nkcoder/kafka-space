package org.nkcoder;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumerExample {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerExample.class);

    public static void main(String[] args) {
        logger.info("Starting Kafka Consumer...");

        Properties props = KafkaProperties.getConsumerProperties();

        KafkaConsumer<String, String> consumer = null;
        boolean shouldCommitOnClose = true; // Track if we should commit on graceful shutdown

        try {
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(KafkaProperties.TOPIC_1));

            logger.info("Consumer subscribed to topic: {}", KafkaProperties.TOPIC_1);
            logger.info("Waiting for messages... (Press Ctrl+C to exit)");

            // Poll for messages
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                try {
                    for (ConsumerRecord<String, String> record : records) {
                        // Process the record - if this throws exception, we won't commit
                        logger.info(
                                "Received record(key={}, value={}, partition={}, offset={})",
                                record.key(),
                                record.value(),
                                record.partition(),
                                record.offset());

                        // Your actual business logic would go here
                        // processRecord(record);
                    }

                    // Only commit if batch was processed successfully (no exception thrown)
                    if (!records.isEmpty()) {
                        logger.info("Received {} messages in this batch.", records.count());

                        // Async commit - doesn't block, allows continued processing
                        // Callback logs any errors but doesn't retry (sync on close handles that)
                        consumer.commitAsync((offsets, exception) -> {
                            if (exception != null) {
                                logger.warn("Async commit failed for offsets: {}", offsets, exception);
                            } else {
                                logger.debug("Async commit succeeded for offsets: {}", offsets);
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.error("Error processing batch, will NOT commit offsets: {}", e.getMessage(), e);
                    shouldCommitOnClose = false; // Don't commit on close if processing failed

                    // Depending on your error handling strategy:
                    // Option 1: Break and stop processing (current behavior)
                    break;

                    // Option 2: Continue to next batch (comment out break above, uncomment below)
                    // continue;

                    // Option 3: Implement retry logic, dead letter queue, etc.
                }
            }
        } catch (Exception e) {
            logger.error("Error in consumer: {}", e.getMessage(), e);
            shouldCommitOnClose = false; // Don't commit if consumer crashed
        } finally {
            // Only do final sync commit if shutdown was graceful and processing was successful
            if (consumer != null) {
                try {
                    if (shouldCommitOnClose) {
                        consumer.commitSync();
                        logger.info("Final synchronous commit completed");
                    } else {
                        logger.warn(
                                "Skipping final commit due to processing errors - failed messages will be reprocessed");
                    }
                } catch (Exception e) {
                    logger.error("Error during final commit: {}", e.getMessage(), e);
                } finally {
                    consumer.close();
                    logger.info("Consumer closed");
                }
            }
        }
    }
}
