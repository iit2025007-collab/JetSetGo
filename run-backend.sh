#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ -z "${MONGODB_URI:-}" ]]; then
  printf "MongoDB Atlas connection string (input hidden): " >&2
  IFS= read -r -s MONGODB_URI
  printf '\n' >&2
  if [[ -z "$MONGODB_URI" ]]; then
    printf "No MongoDB URI provided; backend was not started.\n" >&2
    exit 1
  fi
  export MONGODB_URI
fi

export PAYMENT_MODE="${PAYMENT_MODE:-DEMO}"
export SEED_DEMO_DATA="${SEED_DEMO_DATA:-true}"

exec mvn -f "$ROOT_DIR/pom.xml" spring-boot:run
