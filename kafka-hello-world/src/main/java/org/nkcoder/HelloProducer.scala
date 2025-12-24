package org.nkcoder

import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
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

        sendMessage(producer, KafkaConfig.Topic, key, value)
      }

      // Wait for all sends to complete
      import scala.concurrent.Await
      import scala.concurrent.duration.*

      futures.foreach { future =>
        Try(Await.result(future, 30.seconds)) match
          case Success(metadata) =>
            logger.debug(s"Message delivered: partition=${metadata.partition}, offset=${metadata.offset}")
          case Failure(ex) =>
            logger.error(s"Message delivery failed: ${ex.getMessage}")
      }

      logger.info(s"Successfully sent $messageCount messages to topic '${KafkaConfig.Topic}'")
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

    producer.send(record, new Callback:
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit =
        if exception != null then
          promise.failure(exception)
        else
          promise.success(metadata)
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
