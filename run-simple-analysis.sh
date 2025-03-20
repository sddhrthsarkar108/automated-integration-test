#!/bin/bash

# Run a simple call flow analysis using our lightweight analyzer without JavaParser
# This should work on any Java version without external dependencies

echo "Starting Simple Call Flow Analysis..."

# Set default values
CLASS_NAME="com.sbtl1.mod1.rest.UserController"
METHOD_NAME="getUsersAboveAge"

# Override with command line arguments if provided
if [ "$#" -ge 1 ]; then
    CLASS_NAME="$1"
fi

if [ "$#" -ge 2 ]; then
    METHOD_NAME="$2"
fi

echo "Running code flow analysis for $CLASS_NAME.$METHOD_NAME"

# Set up Java environment using asdf if available
if command -v asdf &> /dev/null; then
    . $HOME/.asdf/asdf.sh
    export JAVA_HOME=$(asdf where java)
fi

# Set up directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD1_DIR="$SCRIPT_DIR/mod1"
OUT_DIR="$SCRIPT_DIR/out"

# Create output directory if it doesn't exist
mkdir -p "$OUT_DIR"

# Compile our Java files
echo "Compiling Java files..."
javac -d "$OUT_DIR" \
      "$MOD1_DIR/src/main/java/com/sbtl1/mod1/util/SimpleCodeFlowAnalyzer.java" \
      "$MOD1_DIR/src/main/java/com/sbtl1/mod1/util/SimpleCallFlowAnalysisDemo.java"

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Run the analysis
echo "Starting analysis..."
java -cp "$OUT_DIR" com.sbtl1.mod1.util.SimpleCallFlowAnalysisDemo "$CLASS_NAME" "$METHOD_NAME"

echo "Analysis complete!" 