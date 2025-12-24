package org.nkcoder

import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*
import scala.util.Using

object HelloConsumer:

  private val logger = LoggerFactory.getLogger(getClass)
  private val keepRunning = AtomicBoolean(true)

  def main(args: Array[String]): Unit =
    val groupId = args.headOption.getOrElse("hello-consumer-group")

    logger.info(s"Starting Kafka Consumer with group '$groupId'")

    // Register shutdown hook for graceful shutdown
    val consumer = KafkaConsumer[String, String](KafkaConfig.consumerProperties(groupId))

    Runtime.getRuntime.addShutdownHook(Thread(() => {
      logger.info("Shutdown signal received, stopping consumer...")
      keepRunning.set(false)
      consumer.wakeup() // Interrupt any blocking poll
    }))

    try
      // Subscribe to topic
      consumer.subscribe(List(KafkaConfig.Topic).asJava)
      logger.info(s"Subscribed to topic '${KafkaConfig.Topic}'")

      // Main poll loop
      while keepRunning.get() do
        try
          val records: ConsumerRecords[String, String] = consumer.poll(Duration.ofMillis(1000))

          if !records.isEmpty then
            processRecords(records)

            // Manual commit after successful processing
            consumer.commitSync()
            logger.debug(s"Committed offsets for ${records.count()} records")

        catch
          case _: WakeupException if !keepRunning.get() =>
            logger.info("Consumer wakeup for shutdown")
          case ex: Exception =>
            logger.error(s"Error in poll loop: ${ex.getMessage}", ex)

        finally
          logger.info("Closing consumer...")
      consumer.close()
      logger.info("Consumer closed successfully")

  /** Process a batch of records */
  private def processRecords(records: ConsumerRecords[String, String]): Unit =
    records.asScala.foreach { record =>
      processRecord(record)
    }

  /** Process a single record - this is where your business logic goes */
  private def processRecord(record: ConsumerRecord[String, String]): Unit =
    logger.info(
      s"""
         |Received message:
         |  Topic:     ${record.topic}
         |  Partition: ${record.partition}
         |  Offset:    ${record.offset}
         |  Timestamp: ${Instant.ofEpochMilli(record.timestamp)}
         |  Key:       ${record.key}
         |  Value:     ${record.value}
         |""".stripMargin
    )

    // Simulate processing time
    Thread.sleep(100)

