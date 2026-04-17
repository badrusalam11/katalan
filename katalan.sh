#!/bin/bash
# katalan Runner - Shell Script
# Usage: ./katalan.sh run -tc test.groovy

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/target/katalan-runner-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: katalan-runner-1.0.0.jar not found!"
    echo "Please build the project first: mvn clean package"
    exit 1
fi

java -jar "$JAR_FILE" "$@"
