package org.nkcoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static void main(String[] args) {
        logger.info("Kafka 101 - Producer and Consumer Example");
        logger.info("==========================================");
        logger.info("");

        if (args.length == 0) {
            logger.info("Usage:");
            logger.info("  Run Producer: ./gradlew run --args='producer'");
            logger.info("  Run Consumer: ./gradlew run --args='consumer'");
            logger.info("");
            logger.info("Or run the classes directly:");
            logger.info("  - org.nkcoder.KafkaProducerExample");
            logger.info("  - org.nkcoder.KafkaConsumerExample");
            return;
        }

        String mode = args[0].toLowerCase();

        switch (mode) {
            case "producer":
                logger.info("Starting Producer...");
                KafkaProducerExample.main(args);
                break;
            case "consumer":
                logger.info("Starting Consumer...");
                KafkaConsumerExample.main(args);
                break;
            default:
                logger.warn("Unknown mode: {}", mode);
                logger.info("Use 'producer' or 'consumer'");
        }
    }
}
