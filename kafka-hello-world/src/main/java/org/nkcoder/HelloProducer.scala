package org.nkcoder

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try, Using}

object HelloProducer:
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit =
    val messageCount = args.headOption.map(_.toInt).getOrElse(10)

    logger.info(s"Starting Kafka Producer - will send $messageCount messages")

    Using.resource(KafkaProducer[String, String](KafkaConfig.producerProperties)) { producer =>
      val futures = (1 to messageCount).map { i =>
        val key = s"key-${i % 3}" // Distributed across 3 keys
        val value = s"""{"id": $i, "message": "Hello Kafka!", "timestamp": "${Instant.now}"}"""
        sendMessage(producer, KafkaConfig.topic, key, value)
      }

      // Wait for all sends to complete
      val allResults = Future.sequence(futures)

      Try(Await.result(allResults, 30.seconds)) match
        case Success(metadataList) =>
          metadataList.foreach { metadata =>
            logger.debug(s"Message delivered: partition=${metadata.partition}, offset=${metadata.offset}")
          }
          logger.info(s"Successfully sent $messageCount messages to topic '${KafkaConfig.topic}'")
        case Failure(ex) =>
          logger.error(s"Message delivery failed: ${ex.getMessage}")
    }


  /** Sends a message asynchronously and returns a Future with metadata */
  private def sendMessage(
    producer: KafkaProducer[String, String],
    topic: String,
    key: String,
    value: String
  ): Future[RecordMetadata] =
    val promise = Promise[RecordMetadata]()
    val record = ProducerRecord[String, String](topic, key, value)

    producer.send(record, (metadata: RecordMetadata, exception: Exception) =>
      Option(exception) match
        case Some(ex) => promise.failure(ex)
        case None => promise.success(metadata)
    )

    promise.future

  /** Synchronous send for simple use cases */
  private def sendMessageSync(
    producer: KafkaProducer[String, String],
    topic: String,
    key: String,
    value: String
  ): Try[RecordMetadata] =
    Try {
      val record = ProducerRecord[String, String](topic, key, value)
      producer.send(record).get() // Blocks until acknowledged
    }
