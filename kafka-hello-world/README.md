# Kafka Hello World

A simple Scala 3 project demonstrating how to produce and consume messages with Apache Kafka 4.1.1.

## Prerequisites

- **JDK 21+** (recommended: JDK 21)
- **sbt** (Scala Build Tool)
- **Apache Kafka 4.1.1** running locally on `localhost:9092`

## Project Structure

```
kafka-hello-world/
├── build.sbt                      # Project dependencies and settings
└── src/main/java/org/nkcoder/
    ├── KafkaConfig.scala          # Kafka producer/consumer configurations
    ├── HelloProducer.scala        # Message producer example
    └── HelloConsumer.scala        # Message consumer example
```

## Starting Kafka

You can start Kafka using Docker (from the parent directory):

```bash
cd ../docker
docker compose up -d
```

Or use the native Kafka installation:

```bash
cd ../kafka_2.13-4.1.1
./bin/kafka-storage.sh format -t $(./bin/kafka-storage.sh random-uuid) -c config/server.properties
./bin/kafka-server-start.sh config/server.properties
```

## Create the Topic

Before running the examples, create the `hello-kafka` topic:

```bash
# Using Docker
docker exec kafka kafka-topics.sh --create --topic hello-kafka --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# Or using native Kafka
../kafka_2.13-4.1.1/bin/kafka-topics.sh --create --topic hello-kafka --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

## Building the Project

```bash
sbt compile
```

## Running the Examples

### Producer

Send messages to Kafka:

```bash
# Send 10 messages (default)
sbt "runMain org.nkcoder.HelloProducer"

# Send a custom number of messages
sbt "runMain org.nkcoder.HelloProducer 50"
```

### Consumer

Consume messages from Kafka:

```bash
# Use default consumer group
sbt "runMain org.nkcoder.HelloConsumer"

# Use a custom consumer group
sbt "runMain org.nkcoder.HelloConsumer my-custom-group"
```

Press `Ctrl+C` to gracefully stop the consumer.

## Key Features

### Producer (`HelloProducer.scala`)

- **Asynchronous sending** with `Future` and `Promise` for non-blocking operations
- **Idempotent producer** enabled for exactly-once semantics
- **LZ4 compression** for efficient message transfer
- **Configurable batching** for improved throughput

### Consumer (`HelloConsumer.scala`)

- **Manual offset commits** for reliable message processing
- **Graceful shutdown** with shutdown hook and `wakeup()`
- **New consumer protocol (KIP-848)** enabled for improved rebalancing
- **Configurable consumer group** for horizontal scaling

### Configuration (`KafkaConfig.scala`)

| Setting | Producer | Consumer |
|---------|----------|----------|
| Bootstrap Servers | `localhost:9092` | `localhost:9092` |
| Acks | `all` | - |
| Idempotence | `true` | - |
| Compression | `lz4` | - |
| Auto Offset Reset | - | `earliest` |
| Auto Commit | - | `false` |
| Group Protocol | - | `consumer` (KIP-848) |

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| kafka-clients | 4.1.1 | Kafka producer/consumer API |
| logback-classic | 1.5.23 | Logging implementation |
| slf4j-api | 2.0.17 | Logging facade |
| jackson-databind | 2.20.1 | JSON serialization |
| jackson-module-scala | 2.20.1 | Scala support for Jackson |

## Troubleshooting

### Connection Refused

Make sure Kafka is running on `localhost:9092`:

```bash
# Check if Kafka is running
docker ps | grep kafka

# Check Kafka logs
docker logs kafka
```

### Topic Not Found

Create the topic manually:

```bash
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Consumer Not Receiving Messages

- Ensure the consumer group hasn't already consumed all messages
- Try using a new consumer group name
- Check if `auto.offset.reset` is set to `earliest`

