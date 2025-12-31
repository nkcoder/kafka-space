package org.nkcoder

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import java.util.Properties

object KafkaConfig:
  private val bootstrapServers = "localhost:29092,localhost:39092"
  val topic = "test-topic-1"

  def producerProperties: Properties =
    val props = Properties()

    // Required configurations
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)

    // Reliability configurations
    props.put(ProducerConfig.ACKS_CONFIG, "all") // Wait for all replicas
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")

    // Performance tuning
    props.put(ProducerConfig.LINGER_MS_CONFIG, "5") // Small batching delay
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384") // 16KB batch size
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4") // Enable compression

    // Retry configuration
    props.put(ProducerConfig.RETRIES_CONFIG, "3")
    props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "100")

    props

  def consumerProperties(groupId: String): Properties =
    val props = Properties()

    // Required configurations
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)

    // Offset management
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest") // Start from beginning
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false") // Manual commit for control

    // New consumer protocol (KIP-848) - opt-in for improved rebalancing
    props.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "consumer")

    // Performance tuning
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1024") // 1KB minimum fetch
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")

    props
