#!/usr/bin/env bash
# Runs the Spark batch job against target/spark-analytics-1.0.0-all.jar.
# Env vars (DB_URL, DB_USERNAME, ... - see README.md) are read from the
# current shell if set; otherwise the job's own local-dev defaults apply.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f target/spark-analytics-1.0.0-all.jar ]; then
  echo "target/spark-analytics-1.0.0-all.jar not found - run 'mvn clean package' first." >&2
  exit 1
fi

java \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
  --add-opens=java.base/sun.security.action=ALL-UNNAMED \
  --add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
  --add-opens=java.management/sun.management=ALL-UNNAMED \
  -cp target/spark-analytics-1.0.0-all.jar \
  com.ecommerce.analytics.spark.batch.SparkBatchAnalyticsJob
