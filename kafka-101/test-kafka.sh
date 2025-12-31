#!/bin/bash

# Script to test Kafka Producer and Consumer

echo "Kafka 101 - Test Script"
echo "======================"
echo ""

# Check if Kafka is running
echo "Checking if Kafka cluster is accessible..."
nc -zv localhost 29092 2>&1 | grep -q succeeded
if [ $? -eq 0 ]; then
    echo "✓ Kafka is accessible on port 29092"
else
    echo "✗ Warning: Could not connect to Kafka on port 29092"
    echo "  Make sure your Kafka cluster is running"
fi

echo ""
echo "Available commands:"
echo "  1. Run Producer: ./gradlew run --args='producer'"
echo "  2. Run Consumer: ./gradlew run --args='consumer'"
echo ""
echo "To test the complete flow:"
echo "  1. Open two terminal windows"
echo "  2. In the first terminal, run: ./gradlew run --args='consumer'"
echo "  3. In the second terminal, run: ./gradlew run --args='producer'"
echo ""

