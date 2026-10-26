#!/usr/bin/env bash
# PHASE 1, item 4: periodic re-run wrapper for the Spark batch job, so daily
# revenue rollups (and the other spark_batch_* tables) stay reasonably fresh
# without a person manually re-running run-batch.sh.
#
# WHY A SHELL LOOP, NOT A SPRING @Scheduled CALL INTO SPARK:
# spark-analytics is a deliberately separate JVM process from the main
# Spring Boot app (see spark-analytics/pom.xml: "Spark jobs run as separate
# JVM processes and communicate with the main application through
# PostgreSQL and Kafka"). Having the Spring app invoke Spark in-process
# would violate that isolation (classpath conflicts between Spark's Scala/
# Guava versions and Spring's own, a long-running Spark job blocking a
# request thread, one JVM crash taking down the other, ...). So "scheduling"
# the batch job here means periodically re-running the existing, unmodified
# run-batch.sh as its own process, the same way a person would run it by
# hand - just looped, with a sleep between runs. This is additive: it calls
# run-batch.sh as-is and does not change how the job is packaged, run once,
# or invoked manually.
#
# Usage:
#   ./run-batch-loop.sh                  # re-run every 300s (5 min) forever
#   BATCH_LOOP_INTERVAL_SECONDS=60 ./run-batch-loop.sh   # custom interval
#   ./run-batch-loop.sh --once           # run exactly once and exit (useful for cron instead of a loop)
#
# Logs each run's start/end/exit code to stdout; a failed run is logged and
# the loop continues (one bad run — e.g. Postgres briefly unreachable —
# should not permanently stop future rollups).
set -uo pipefail
cd "$(dirname "$0")"

INTERVAL_SECONDS="${BATCH_LOOP_INTERVAL_SECONDS:-300}"
RUN_ONCE=false

for arg in "$@"; do
  case "$arg" in
    --once) RUN_ONCE=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

run_once() {
  local start_ts
  start_ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "[run-batch-loop] starting batch job at ${start_ts}"

  if ./run-batch.sh; then
    echo "[run-batch-loop] batch job finished OK at $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  else
    local exit_code=$?
    echo "[run-batch-loop] batch job FAILED (exit ${exit_code}) at $(date -u +"%Y-%m-%dT%H:%M:%SZ") - will retry next cycle" >&2
  fi
}

if [ "$RUN_ONCE" = true ]; then
  run_once
  exit 0
fi

echo "[run-batch-loop] looping every ${INTERVAL_SECONDS}s. Ctrl+C to stop."
while true; do
  run_once
  sleep "$INTERVAL_SECONDS"
done
