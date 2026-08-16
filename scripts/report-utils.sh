#!/bin/bash

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
  echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
  echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

# Check if file exists
require_file() {
  if [ ! -f "$1" ]; then
    log_error "File not found: $1"
    exit 1
  fi
}

# Create directory if not exists
ensure_dir() {
  mkdir -p "$1"
}

# Extract JSON value
json_get() {
  local json_file=$1
  local key=$2
  grep -o "\"$key\"[^,]*" "$json_file" | head -1 | cut -d'"' -f4
}

# Convert microseconds to milliseconds
us_to_ms() {
  echo "scale=2; $1 / 1000" | bc
}

# Create markdown header
md_h1() {
  echo "# $1"
  echo ""
}

md_h2() {
  echo "## $1"
  echo ""
}

md_h3() {
  echo "### $1"
  echo ""
}

# Create markdown code block
md_code() {
  echo '```'
  echo "$1"
  echo '```'
  echo ""
}

# Comparison helper
compare_value() {
  local value=$1
  local threshold=$2
  if (( $(echo "$value > $threshold" | bc -l) )); then
    echo "✓"
  else
    echo "⚠️"
  fi
}