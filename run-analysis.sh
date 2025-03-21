#!/bin/bash

# This script demonstrates the call flow analysis capability using the JavaParserCodeFlowAnalyzer
# through the Spring Boot REST API endpoints.

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Default values
CLASS_NAME=${1:-"com.sbtl1.mod1.rest.UserController"}
METHOD_NAME=${2:-"getUsersAboveAge"}

# Extract the short name without package for API calls
SHORT_CLASS=$(echo $CLASS_NAME | sed 's/.*\.//')
PACKAGE_PATH=$(echo $CLASS_NAME | grep -o '.*\..*\.' | sed 's/com\.sbtl1\.mod1\.//' | sed 's/\.$//')

echo "Running code flow analysis for $CLASS_NAME.$METHOD_NAME via REST API"

# Build the project
./gradlew :mod1:build

# Kill any existing app instances
echo "Checking for existing application instances..."
pkill -f "com.sbtl1.mod1.App" || true
sleep 2

# Start the Spring Boot application in the background
echo "Starting Spring Boot application..."
./gradlew :mod1:bootRun &
APP_PID=$!

# Wait for app to start with timeout
echo "Waiting for application to start..."
MAX_RETRIES=30
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -s http://localhost:32000/mod1/api/codeanalysis/flow/rest/UserController/getUsersAboveAge > /dev/null 2>&1; then
        echo "Application is up and running"
        break
    fi
    echo "Waiting... ($(($RETRY_COUNT + 1))/$MAX_RETRIES)"
    sleep 2
    RETRY_COUNT=$(($RETRY_COUNT + 1))
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "Application failed to start within the timeout period"
    kill $APP_PID 2>/dev/null || true
    exit 1
fi

# Make the API calls
echo "Calling the flow analysis endpoint..."
curl -s "http://localhost:32000/mod1/api/codeanalysis/flow/$PACKAGE_PATH/$SHORT_CLASS/$METHOD_NAME" | jq '.'

echo "Calling the code snippets endpoint..."
# Don't use jq for snippets since it's returning Markdown, not JSON
curl -s "http://localhost:32000/mod1/api/codeanalysis/snippets/$PACKAGE_PATH/$SHORT_CLASS/$METHOD_NAME"

# Clean up - stop the Spring Boot application
echo "Stopping Spring Boot application..."
kill $APP_PID 2>/dev/null || true
pkill -f "com.sbtl1.mod1.App" || true

echo "Analysis complete." 