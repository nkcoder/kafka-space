# Kafka 101 - Producer and Consumer Example

This project demonstrates how to create a Kafka producer and consumer to interact with a local Kafka cluster.

## Prerequisites

- Kafka cluster running locally on ports: 29092, 39092, 49092
- Java 17 or higher
- Gradle

## Setup

1. Ensure your Kafka cluster is running
2. Build the project:
   ```bash
   ./gradlew build
   ```

## Running the Examples

### Option 1: Run via Main class

Run the producer:
```bash
./gradlew run --args='producer'
```

Run the consumer (in a separate terminal):
```bash
./gradlew run --args='consumer'
```

### Option 2: Run classes directly

Run the producer:
```bash
./gradlew run --args='producer' -PmainClass=org.nkcoder.KafkaProducerExample
```

Run the consumer:
```bash
./gradlew run --args='consumer' -PmainClass=org.nkcoder.KafkaConsumerExample
```

### Option 3: Run from IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Navigate to `KafkaProducerExample.java` or `KafkaConsumerExample.java`
3. Click the green run button next to the main method

## What the Examples Do

### Producer (`KafkaProducerExample`)
- Connects to the Kafka cluster at localhost:29092,39092,49092
- Sends 10 test messages to the topic `test-topic`
- Each message has a key (`key-0` to `key-9`) and a value with a timestamp
- Prints confirmation for each sent message with partition and offset information

### Consumer (`KafkaConsumerExample`)
- Connects to the same Kafka cluster
- Subscribes to the `test-topic` topic
- Uses consumer group `test-consumer-group`
- Reads messages from the earliest offset
- Prints each received message with its metadata
- Runs continuously until stopped (Ctrl+C)

## Configuration

Both producer and consumer are configured to connect to:
- Bootstrap servers: `localhost:29092,localhost:39092,localhost:49092`
- Topic: `test-topic`

You can modify these settings in the respective Java files.

## Testing the Setup

1. Start the consumer in one terminal
2. Start the producer in another terminal
3. You should see the producer sending messages and the consumer receiving them

## Troubleshooting

If you encounter connection issues:
1. Verify your Kafka cluster is running: `docker ps` or check your Kafka process
2. Ensure the ports (29092, 39092, 49092) are accessible
3. Check if the topic exists or allow auto-creation of topics in Kafka configuration

