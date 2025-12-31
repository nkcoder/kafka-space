# Kafka Offset Commit - Quick Reference Card

## 🎯 The Golden Rule
**Never commit offsets for messages you didn't successfully process!**

---

## ✅ The Correct Pattern (Production-Ready)

```java
KafkaConsumer<String, String> consumer = null;
boolean shouldCommitOnClose = true;  // Track overall success

try {
    consumer = new KafkaConsumer<>(props);
    consumer.subscribe(topics);
    
    while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
        boolean batchSuccess = true;  // Track batch success
        
        try {
            // Process all records in batch
            for (ConsumerRecord<String, String> record : records) {
                processRecord(record);  // Your business logic
            }
        } catch (Exception e) {
            logger.error("Error processing batch, will NOT commit: {}", e.getMessage());
            batchSuccess = false;
            shouldCommitOnClose = false;
            break;  // Or 'continue' depending on your strategy
        }
        
        // Only commit if processing was successful
        if (batchSuccess && !records.isEmpty()) {
            consumer.commitAsync((offsets, exception) -> {
                if (exception != null) {
                    logger.warn("Async commit failed: {}", exception.getMessage());
                }
            });
        }
    }
} catch (Exception e) {
    logger.error("Consumer error: {}", e.getMessage());
    shouldCommitOnClose = false;
} finally {
    if (consumer != null) {
        try {
            if (shouldCommitOnClose) {
                consumer.commitSync();  // Final sync commit
                logger.info("Final commit completed");
            } else {
                logger.warn("Skipping commit - failed messages will be reprocessed");
            }
        } finally {
            consumer.close();
        }
    }
}
```

---

## 🚫 Common Mistakes to Avoid

### ❌ WRONG: Always commit in finally
```java
try {
    while (true) {
        records = consumer.poll();
        process(records);  // May throw exception
        consumer.commitAsync();
    }
} finally {
    consumer.commitSync();  // ← ALWAYS commits, even on failure!
}
// Problem: Failed messages are marked as processed = DATA LOSS
```

### ❌ WRONG: Commit before processing
```java
records = consumer.poll();
consumer.commitSync();    // ← Commit first
process(records);         // ← Then process
// Problem: If crash during processing = DATA LOSS (at-most-once)
```

### ❌ WRONG: Ignore processing errors
```java
try {
    process(records);
} catch (Exception e) {
    logger.error("Error"); // Log but continue
}
consumer.commitSync();    // ← Commits even though processing failed!
// Problem: Failed messages marked as processed = DATA LOSS
```

---

## ✅ Best Practices Checklist

- ✅ **Track processing success** before committing
- ✅ **Process FIRST, commit AFTER** (at-least-once)
- ✅ **Use async commits** during normal processing (performance)
- ✅ **Use sync commit** on shutdown (safety)
- ✅ **Skip commit** if processing failed
- ✅ **Log commit decisions** for debugging
- ✅ **Make processing idempotent** (handle duplicates)
- ✅ **Close consumer** in finally block

---

## 🎭 Processing Guarantees

### At-Most-Once (NOT RECOMMENDED)
```java
consumer.commitSync();  // Commit first
process(records);       // Process after
// If crash: Message lost ❌
```

### At-Least-Once (RECOMMENDED) ✅
```java
process(records);       // Process first
consumer.commitSync();  // Commit after
// If crash: Message reprocessed (duplicate) ✅
// Better: duplicate than lost!
```

### Exactly-Once (Advanced)
```java
// Requires Kafka transactions + idempotent processing
producer.initTransactions();
producer.beginTransaction();
// ... process and produce ...
producer.sendOffsetsToTransaction(offsets, consumerGroupId);
producer.commitTransaction();
// Complex but guarantees exactly-once
```

---

## 🛠️ Error Handling Strategies

### Strategy 1: Stop on Error (Safest)
```java
catch (Exception e) {
    batchSuccess = false;
    shouldCommitOnClose = false;
    break;  // Stop processing, needs restart
}
```
**Use when:** Critical data, need immediate attention

### Strategy 2: Skip Failed Batch
```java
catch (Exception e) {
    batchSuccess = false;
    continue;  // Skip this batch, continue processing
}
```
**Use when:** Transient errors, consumer should keep running

### Strategy 3: Dead Letter Queue
```java
catch (Exception e) {
    sendToDeadLetterQueue(record);  // Save for later
    // Continue processing other messages
}
```
**Use when:** Want to analyze failures later

---

## 📊 Commit Methods Comparison

| Method | Blocking | Speed | Safety | Use Case |
|--------|----------|-------|--------|----------|
| `commitSync()` | Yes | Slow | High | Final commit on shutdown |
| `commitAsync()` | No | Fast | Medium | During normal processing |
| `commitSync(offsets)` | Yes | Slow | High | Specific offset control |
| `commitAsync(offsets, callback)` | No | Fast | Medium | Custom offset + error handling |

---

## 🔍 Debugging Tips

### Check Consumer Group Offsets
```bash
kafka-consumer-groups --bootstrap-server localhost:29092 \
  --group your-consumer-group \
  --describe
```

### Reset Offsets (for testing)
```bash
# Reset to beginning
kafka-consumer-groups --bootstrap-server localhost:29092 \
  --group your-consumer-group \
  --topic your-topic \
  --reset-offsets --to-earliest --execute

# Reset to specific offset
kafka-consumer-groups --bootstrap-server localhost:29092 \
  --group your-consumer-group \
  --topic your-topic:0 \
  --reset-offsets --to-offset 100 --execute
```

### Log Analysis
Look for these log messages:
- ✅ "Final synchronous commit completed" = Clean shutdown
- ⚠️ "Skipping final commit" = Processing failed, will retry
- ❌ "Async commit failed" = Temporary issue, final sync will handle

---

## ⚡ Quick Decision Tree

```
Do you need exactly-once semantics?
├─ YES → Use Kafka transactions (complex)
└─ NO  → Use at-least-once (recommended)
         │
         ├─ Make processing idempotent (handle duplicates)
         │
         ├─ Process FIRST, commit AFTER
         │
         ├─ Track success with boolean flags
         │
         ├─ Skip commit if processing failed
         │
         └─ Use hybrid approach:
            ├─ Async commits during processing (fast)
            └─ Sync commit on shutdown (safe)
```

---

## 💾 Configuration Tips

```java
Properties props = new Properties();

// Manual offset management (recommended for production)
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

// Start from earliest/latest/none
props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

// Control batch size
props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");

// Session timeout (consumer considered dead after this)
props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");

// Max time between polls (rebalance if exceeded)
props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");

// Heartbeat interval
props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000");
```

---

## 📈 Performance Tips

1. **Batch Processing**: Process multiple records before committing
2. **Async Commits**: Use during normal operation for speed
3. **Sync on Close**: Only use sync when shutting down
4. **Adjust Batch Size**: `max.poll.records` based on processing time
5. **Connection Pooling**: Reuse database connections
6. **Parallel Processing**: Use multiple consumers in same group

---

## 🎓 Remember

1. **At-least-once is better than data loss**
   - Duplicates can be handled with idempotency
   - Lost data is unrecoverable

2. **Always track processing success**
   - Use boolean flags
   - Only commit on success

3. **Async for speed, Sync for safety**
   - Async during normal operation
   - Sync on shutdown

4. **Log your commit decisions**
   - Makes debugging easier
   - Shows why commits were skipped

5. **Test failure scenarios**
   - Simulate errors
   - Verify offset behavior
   - Check reprocessing works

---

## 📚 Your Project Files

- **KafkaConsumerExample.java** - Main consumer with error handling
- **KafkaConsumerOffsetStrategies.java** - 6 different strategies
- **OFFSET_COMMIT_GUIDE.md** - Complete documentation
- **This File** - Quick reference

---

## ✅ Final Checklist

Before deploying to production:

- ✅ Offset commits are conditional (only on success)
- ✅ Processing errors are caught and logged
- ✅ Failed batches won't be committed
- ✅ Consumer closes properly in finally
- ✅ Idempotent processing implemented
- ✅ Error handling strategy chosen
- ✅ Logging shows commit decisions
- ✅ Tested with simulated errors
- ✅ Verified offset behavior
- ✅ Consumer group monitoring set up

---

## 🚀 You're Production Ready!

Your Kafka consumer now implements industry best practices for reliable message processing with proper offset management!

---

**Keep this card handy when implementing Kafka consumers!** 📋

