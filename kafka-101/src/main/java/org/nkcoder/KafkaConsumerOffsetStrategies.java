package org.nkcoder;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class demonstrates different offset commit strategies for Kafka consumers. Choose the strategy that best fits
 * your use case.
 */
public class KafkaConsumerOffsetStrategies {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerOffsetStrategies.class);

    /**
     * Strategy 1: Synchronous commit after each batch Pros: Safest - guarantees offsets are committed before processing
     * next batch Cons: Slower - blocks until commit completes Use when: Data loss is unacceptable and you can afford
     * the latency
     *
     * <p>IMPORTANT: Only commits if processing was successful to prevent data loss!
     */
    public static void syncCommitAfterBatch() {
        logger.info("Starting Consumer with Sync Commit Strategy...");
        Properties props = KafkaProperties.getConsumerProperties();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(KafkaProperties.TOPIC_1));
            logger.info("Consumer subscribed to topic: {}", KafkaProperties.TOPIC_1);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                try {
                    for (ConsumerRecord<String, String> record : records) {
                        // Process the record - if this throws exception, we won't commit
                        logger.info("Processing: key={}, value={}", record.key(), record.value());
                    }
                } catch (Exception e) {
                    logger.error("Error processing batch, will NOT commit: {}", e.getMessage(), e);
                    break; // Stop processing on error
                }

                // Only commit if batch was processed successfully
                if (!records.isEmpty()) {
                    // Synchronous commit - blocks until complete
                    try {
                        consumer.commitSync();
                        logger.info("Committed {} records synchronously", records.count());
                    } catch (Exception e) {
                        logger.error("Commit failed: {}", e.getMessage(), e);
                        // Handle commit failure - maybe retry or stop processing
                        break;
                    }
                }
            }
        }
    }

    /**
     * Strategy 2: Asynchronous commit after each batch Pros: Fast - doesn't block processing Cons: May lose some
     * commits if consumer crashes before commit completes Use when: You can tolerate some duplicate processing and want
     * high throughput
     */
    public static void asyncCommitAfterBatch() {
        logger.info("Starting Consumer with Async Commit Strategy...");
        Properties props = KafkaProperties.getConsumerProperties();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(KafkaProperties.TOPIC_1));
            logger.info("Consumer subscribed to topic: {}", KafkaProperties.TOPIC_1);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    logger.info("Processing: key={}, value={}", record.key(), record.value());
                }

                if (!records.isEmpty()) {
                    // Asynchronous commit with callback
                    consumer.commitAsync((offsets, exception) -> {
                        if (exception != null) {
                            logger.error("Async commit failed for offsets: {}", offsets, exception);
                        } else {
                            logger.debug("Async commit succeeded for offsets: {}", offsets);
                        }
                    });
                }
            }
        }
    }

    /**
     * Strategy 3: Hybrid - Async during processing, Sync on close (RECOMMENDED) Pros: Fast during normal operation,
     * safe on shutdown Cons: Slightly more complex Use when: You want both performance and safety (most common pattern)
     */
    public static void hybridCommitStrategy() {
        logger.info("Starting Consumer with Hybrid Commit Strategy...");
        Properties props = KafkaProperties.getConsumerProperties();

        KafkaConsumer<String, String> consumer = null;
        boolean shouldCommitOnClose = true;

        try {
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(KafkaProperties.TOPIC_1));
            logger.info("Consumer subscribed to topic: {}", KafkaProperties.TOPIC_1);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                try {
                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Processing: key={}, value={}", record.key(), record.value());
                        // If processing throws exception, we won't commit
                    }

                    // Only commit if batch was processed successfully
                    if (!records.isEmpty()) {
                        // Async commit during normal processing
                        consumer.commitAsync((offsets, exception) -> {
                            if (exception != null) {
                                logger.warn("Async commit failed: {}", exception.getMessage());
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.error("Error processing batch, will NOT commit: {}", e.getMessage(), e);
                    shouldCommitOnClose = false;
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error in consumer: {}", e.getMessage(), e);
            shouldCommitOnClose = false;
        } finally {
            // Sync commit before closing ONLY if processing was successful
            if (consumer != null) {
                try {
                    if (shouldCommitOnClose) {
                        consumer.commitSync();
                        logger.info("Final synchronous commit completed");
                    } else {
                        logger.warn("Skipping final commit - failed messages will be reprocessed");
                    }
                } catch (Exception e) {
                    logger.error("Error during final commit: {}", e.getMessage(), e);
                } finally {
                    consumer.close();
                }
            }
        }
    }

    /**
     * Strategy 4: Commit after processing each record Pros: No duplicate processing on restart (processes exactly where
     * it left off) Cons: Very slow - commits for every message Use when: Each message is expensive to process and you
     * must avoid reprocessing
     */
    public static void commitAfterEachRecord() {
        logger.info("Starting Consumer with Per-Record Commit Strategy...");
        Properties props = KafkaProperties.getConsumerProperties();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(KafkaProperties.TOPIC_1));
            logger.info("Consumer subscribed to topic: {}", KafkaProperties.TOPIC_1);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    logger.info("Processing: key={}, value={}", record.key(), record.value());

                    // Process the record...
                    // Then commit just this record's offset
                    Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                    offsets.put(
                            new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1));

                    try {
                        consumer.commitSync(offsets);
                        logger.debug("Committed offset {} for partition {}", record.offset(), record.partition());
                    } catch (Exception e) {
                        logger.error("Failed to commit offset: {}", e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Strategy 5: Periodic commits (time-based) Pros: Good balance between performance and safety Cons: Some duplicate
     * processing possible within the time window Use when: You want to limit duplicate processing window without
     * committing every batch
     */
    public static void periodicCommit() {
        logger.info("Starting Consumer with Periodic Commit Strategy...");
        Properties props = KafkaProperties.getConsumerProperties();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(KafkaProperties.TOPIC_1));
            logger.info("Consumer subscribed to topic: {}", KafkaProperties.TOPIC_1);

            long lastCommitTime = System.currentTimeMillis();
            final long COMMIT_INTERVAL_MS = 5000; // Commit every 5 seconds

            try {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Processing: key={}, value={}", record.key(), record.value());
                    }

                    // Commit based on time interval
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastCommitTime > COMMIT_INTERVAL_MS) {
                        consumer.commitAsync();
                        lastCommitTime = currentTime;
                        logger.info("Periodic commit triggered");
                    }
                }
            } finally {
                consumer.commitSync();
                logger.info("Final commit completed");
            }
        }
    }

    /**
     * Strategy 6: Commit specific partitions/offsets (fine-grained control) Pros: Maximum control over what gets
     * committed Cons: More complex to implement Use when: You need to control exactly which offsets are committed
     * (e.g., processing out of order)
     */
    public static void manualOffsetControl() {
        logger.info("Starting Consumer with Manual Offset Control Strategy...");
        Properties props = KafkaProperties.getConsumerProperties();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(KafkaProperties.TOPIC_1));
            logger.info("Consumer subscribed to topic: {}", KafkaProperties.TOPIC_1);

            Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    logger.info("Processing: key={}, value={}", record.key(), record.value());

                    // Track the offset we want to commit
                    // Note: Committed offset should be the next offset to read, so add 1
                    currentOffsets.put(
                            new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1, "custom metadata"));
                }

                if (!records.isEmpty()) {
                    // Commit the specific offsets we've tracked
                    try {
                        consumer.commitSync(currentOffsets);
                        logger.info("Committed offsets: {}", currentOffsets);
                        currentOffsets.clear();
                    } catch (Exception e) {
                        logger.error("Commit failed: {}", e.getMessage(), e);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            logger.info("Usage: Specify a strategy:");
            logger.info("  1 - Synchronous commit after each batch");
            logger.info("  2 - Asynchronous commit after each batch");
            logger.info("  3 - Hybrid (async + sync on close) - RECOMMENDED");
            logger.info("  4 - Commit after each record");
            logger.info("  5 - Periodic commits (time-based)");
            logger.info("  6 - Manual offset control");
            return;
        }

        String strategy = args[0];
        switch (strategy) {
            case "1":
                syncCommitAfterBatch();
                break;
            case "2":
                asyncCommitAfterBatch();
                break;
            case "3":
                hybridCommitStrategy();
                break;
            case "4":
                commitAfterEachRecord();
                break;
            case "5":
                periodicCommit();
                break;
            case "6":
                manualOffsetControl();
                break;
            default:
                logger.warn("Unknown strategy: {}", strategy);
        }
    }
}
