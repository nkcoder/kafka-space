# Kafka Consumer Offset Commit Strategies

## Overview

When using Kafka consumers with manual offset management (`enable.auto.commit=false`), you need to explicitly commit offsets to track which messages have been processed. This prevents message loss and controls duplicate processing on consumer restarts.

## Key Concepts

### What are Offsets?
- Offsets are position markers in a Kafka partition
- Each message has a unique offset (sequential number)
- Committing an offset tells Kafka "I've processed up to this point"
- The **committed offset** should be the **next message to read** (current offset + 1)

### Auto-Commit vs Manual Commit
```java
// Auto-commit (simple but less control)
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000"); // Commits every 5 seconds

// Manual commit (more control, your current setup)
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
```

## Commit Strategies Comparison

| Strategy | Pros | Cons | Use Case |
|----------|------|------|----------|
| **Sync after batch** | Safest, no duplicates | Slowest, blocks processing | Critical data, can't lose messages |
| **Async after batch** | Fast, non-blocking | May lose commits on crash | High throughput, can tolerate duplicates |
| **Hybrid (RECOMMENDED)** | Fast + safe on shutdown | Slightly complex | Most production use cases |
| **Per-record commit** | Minimal duplicates | Very slow | Expensive processing per message |
| **Periodic (time-based)** | Balanced throughput | Some duplicates possible | Streaming/real-time pipelines |
| **Manual control** | Maximum flexibility | Complex implementation | Custom processing logic |

## Strategy Details

### 1. Synchronous Commit After Batch ⚡ SAFE but SLOW

```java
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);
    }
    
    if (!records.isEmpty()) {
        try {
            consumer.commitSync();  // Blocks until commit completes
            logger.info("Committed {} records", records.count());
        } catch (CommitFailedException e) {
            logger.error("Commit failed: {}", e.getMessage());
            // Handle failure - maybe retry or stop processing
        }
    }
}
```

**When to use:** Financial transactions, order processing, audit logs

---

### 2. Asynchronous Commit After Batch ⚡ FAST but RISKY

```java
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);
    }
    
    if (!records.isEmpty()) {
        // Non-blocking commit with callback
        consumer.commitAsync((offsets, exception) -> {
            if (exception != null) {
                logger.error("Commit failed: {}", exception.getMessage());
            } else {
                logger.debug("Commit succeeded: {}", offsets);
            }
        });
    }
}
```

**When to use:** Analytics, logs, metrics where some duplication is acceptable

---

### 3. Hybrid Approach ✅ RECOMMENDED

```java
try {
    while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
        
        for (ConsumerRecord<String, String> record : records) {
            processRecord(record);
        }
        
        if (!records.isEmpty()) {
            // Async during normal operation for speed
            consumer.commitAsync((offsets, exception) -> {
                if (exception != null) {
                    logger.warn("Async commit failed: {}", exception.getMessage());
                }
            });
        }
    }
} finally {
    try {
        // Sync on shutdown to ensure last batch is saved
        consumer.commitSync();
        logger.info("Final commit completed");
    } catch (Exception e) {
        logger.error("Final commit failed: {}", e.getMessage());
    } finally {
        consumer.close();
    }
}
```

**When to use:** Most production scenarios - good balance of speed and safety

**This is the approach used in your `KafkaConsumerExample.java`**

---

### 4. Per-Record Commit ⚡ PRECISE but VERY SLOW

```java
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);
        
        // Commit after each record
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        offsets.put(
            new TopicPartition(record.topic(), record.partition()),
            new OffsetAndMetadata(record.offset() + 1)  // +1 = next offset to read
        );
        
        consumer.commitSync(offsets);
    }
}
```

**When to use:** Each message takes minutes to process and you can't afford reprocessing

---

### 5. Periodic Commit (Time-Based) ⏱️ BALANCED

```java
long lastCommitTime = System.currentTimeMillis();
final long COMMIT_INTERVAL_MS = 5000;  // Every 5 seconds

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);
    }
    
    // Commit based on time, not batch size
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastCommitTime > COMMIT_INTERVAL_MS) {
        consumer.commitAsync();
        lastCommitTime = currentTime;
        logger.info("Periodic commit triggered");
    }
}
```

**When to use:** Streaming pipelines with steady message flow

---

### 6. Manual Offset Control 🎯 ADVANCED

```java
Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);
        
        // Track offsets manually
        currentOffsets.put(
            new TopicPartition(record.topic(), record.partition()),
            new OffsetAndMetadata(record.offset() + 1, "custom metadata")
        );
    }
    
    if (!records.isEmpty()) {
        consumer.commitSync(currentOffsets);
        currentOffsets.clear();
    }
}
```

**When to use:** Complex processing logic, custom retry mechanisms, out-of-order processing

---

## Common Patterns and Best Practices

### Pattern 1: Batch Processing with Error Handling
```java
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    
    boolean allSuccessful = true;
    for (ConsumerRecord<String, String> record : records) {
        try {
            processRecord(record);
        } catch (Exception e) {
            logger.error("Failed to process record: {}", record, e);
            allSuccessful = false;
            break;  // Stop processing this batch
        }
    }
    
    if (allSuccessful && !records.isEmpty()) {
        consumer.commitSync();
    }
}
```

### Pattern 2: At-Least-Once vs At-Most-Once

**At-Least-Once** (commit AFTER processing):
```java
for (ConsumerRecord<String, String> record : records) {
    processRecord(record);  // Process first
}
consumer.commitSync();      // Then commit
// If crash occurs after processing but before commit: DUPLICATE on restart
```

**At-Most-Once** (commit BEFORE processing):
```java
consumer.commitSync();      // Commit first
for (ConsumerRecord<String, String> record : records) {
    processRecord(record);  // Then process
}
// If crash occurs after commit but before processing: MESSAGE LOST
```

> **Recommendation:** Always use At-Least-Once (commit after processing) and make your processing idempotent.

### Pattern 3: Idempotent Processing

Make your processing handle duplicates gracefully:
```java
for (ConsumerRecord<String, String> record : records) {
    String messageId = record.key();
    
    // Check if already processed (use database, cache, etc.)
    if (alreadyProcessed(messageId)) {
        logger.info("Skipping duplicate message: {}", messageId);
        continue;
    }
    
    processRecord(record);
    markAsProcessed(messageId);
}
consumer.commitSync();
```

---

## Troubleshooting

### Problem: Messages being reprocessed on restart
**Cause:** Offsets not being committed  
**Solution:** Ensure you're committing offsets after processing

### Problem: Messages being skipped
**Cause:** Committing before processing completes  
**Solution:** Always commit AFTER successful processing

### Problem: CommitFailedException
**Cause:** Consumer took too long between polls (exceeded `max.poll.interval.ms`)  
**Solution:** 
- Reduce batch size
- Increase `max.poll.interval.ms`
- Process messages faster
```java
props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "600000"); // 10 minutes
```

### Problem: Duplicate processing despite commits
**Cause:** Consumer crashed between processing and commit  
**Solution:** This is normal with at-least-once. Make processing idempotent.

---

## Configuration Tips

### Important Consumer Properties
```java
Properties props = new Properties();

// Offset management
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // or "latest"

// Control batch size
props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");

// Session timeout (how long before consumer is considered dead)
props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");

// Max time between polls
props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");

// Heartbeat interval
props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000");
```

---

## Testing Your Strategy

### Test 1: Normal Operation
1. Start producer
2. Start consumer
3. Verify messages are processed and committed

### Test 2: Consumer Crash
1. Start consumer
2. Process some messages
3. Kill consumer (Ctrl+C) mid-batch
4. Restart consumer
5. Verify: No messages lost, minimal duplicates

### Test 3: Slow Processing
1. Add delay in processing (e.g., Thread.sleep(5000))
2. Verify consumer doesn't get kicked out of group
3. Verify commits still work

---

## Running the Examples

Your project now has two consumer implementations:

### 1. KafkaConsumerExample (Hybrid Strategy)
```bash
./gradlew run --args='consumer'
```

### 2. KafkaConsumerOffsetStrategies (All Strategies)
```bash
# Strategy 1: Sync commit
./gradlew run --args='org.nkcoder.KafkaConsumerOffsetStrategies 1'

# Strategy 2: Async commit
./gradlew run --args='org.nkcoder.KafkaConsumerOffsetStrategies 2'

# Strategy 3: Hybrid (recommended)
./gradlew run --args='org.nkcoder.KafkaConsumerOffsetStrategies 3'

# Strategy 4: Per-record commit
./gradlew run --args='org.nkcoder.KafkaConsumerOffsetStrategies 4'

# Strategy 5: Periodic commit
./gradlew run --args='org.nkcoder.KafkaConsumerOffsetStrategies 5'

# Strategy 6: Manual offset control
./gradlew run --args='org.nkcoder.KafkaConsumerOffsetStrategies 6'
```

---

## Quick Reference

| What You Need | Use This Strategy |
|---------------|-------------------|
| Best performance | Async commit |
| Best safety | Sync commit |
| Balanced (recommended) | Hybrid (async + sync on close) |
| Minimal duplicates | Per-record commit |
| Steady message flow | Periodic commit |
| Complex logic | Manual offset control |

---

## Summary

Your current implementation uses the **Hybrid strategy (Strategy 3)**, which is the **recommended approach** for most production use cases because it:
- ✅ Uses async commits for speed during normal operation
- ✅ Uses sync commit on shutdown to ensure data safety
- ✅ Provides proper error handling and logging
- ✅ Balances throughput with reliability

This gives you the best of both worlds: fast processing with guaranteed commits on graceful shutdown! 🚀

