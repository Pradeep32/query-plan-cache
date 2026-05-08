#!/bin/bash
# ============================================================
# run_challenge.sh — Build, Test & Verify Query Plan Cache
# ============================================================
# This script compiles the project, runs all unit/integration
# tests, and prints a summary of results for verification.
# ============================================================

set -e

echo "============================================================"
echo "  Query Plan Cache — Build & Test Verification"
echo "============================================================"
echo ""

echo ">>> Step 1: Cleaning previous build artifacts..."
mvn clean -q
echo "    [DONE] Clean successful."
echo ""

echo ">>> Step 2: Compiling source code..."
mvn compile -q
echo "    [DONE] Compilation successful."
echo ""

echo ">>> Step 3: Running all unit & integration tests..."
echo "------------------------------------------------------------"
mvn test -Dsurefire.useFile=false 2>&1
echo "------------------------------------------------------------"
echo ""

echo "============================================================"
echo "  BUILD & TEST VERIFICATION COMPLETE"
echo "============================================================"
echo ""
echo "Custom Logic Changes Implemented:"
echo "  1. Adaptive TTL (frequency-based TTL extension)"
echo "  2. Query Complexity Scoring Algorithm"
echo "  3. Cache Warm-up Strategy"
echo "  4. NULL Literal Preservation in Normalizer"
echo "  5. DML Table Extraction for targeted invalidation"
echo ""
echo "============================================================"
