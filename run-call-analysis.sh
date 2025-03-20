#!/bin/bash

# Script to run the Simple Call Flow Analysis Demo
# This demonstrates the call flow analysis capability without requiring a Spring Boot build

# Set up directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD1_DIR="$SCRIPT_DIR/mod1"
OUT_DIR="$SCRIPT_DIR/out"

mkdir -p "$OUT_DIR"

# Compile the Java files
echo "Compiling Java files..."
javac -d "$OUT_DIR" "$MOD1_DIR/src/main/java/com/sbtl1/mod1/util/SimpleCodeFlowAnalyzer.java" "$MOD1_DIR/src/main/java/com/sbtl1/mod1/util/CallFlowAnalysisDemo.java"

# Run the demo
if [ $# -lt 2 ]; then
    CLASS_NAME="com.sbtl1.mod1.rest.UserController"
    METHOD_NAME="getUsersAboveAge"
    echo "No class/method specified, using default: $CLASS_NAME.$METHOD_NAME"
else
    CLASS_NAME="$1"
    METHOD_NAME="$2"
fi

echo "Running call flow analysis for $CLASS_NAME.$METHOD_NAME"
java -cp "$OUT_DIR" com.sbtl1.mod1.util.CallFlowAnalysisDemo "$CLASS_NAME" "$METHOD_NAME" 