#!/usr/bin/env bash
# Polls /health until it reports UP (up to ~90 s). Used by the Docker Compose CI job.
for i in $(seq 1 30); do
  if curl -fs http://localhost:8080/health | grep -q '"status":"UP"'; then
    curl -s http://localhost:8080/health
    echo
    exit 0
  fi
  sleep 3
done
echo "App did not become healthy in time"
exit 1
