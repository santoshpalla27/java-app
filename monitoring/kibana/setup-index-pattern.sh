#!/bin/bash

# Kibana Index Pattern Setup Script
# This script creates the index pattern for OpenTelemetry logs in Kibana

echo "Setting up Kibana index pattern for OpenTelemetry logs..."

# Wait for Kibana to be ready
echo "Waiting for Kibana to be ready..."
until curl -s http://localhost:5601/api/status | grep -q '"level":"available"'; do
    echo "Kibana not ready yet, waiting..."
    sleep 5
done

echo "Kibana is ready!"

# Create index pattern for otel-logs
echo "Creating index pattern for otel-logs..."
curl -X POST "http://localhost:5601/api/saved_objects/index-pattern/otel-logs" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "title": "otel-logs*",
      "timeFieldName": "@timestamp"
    }
  }'

echo ""
echo "✅ Kibana index pattern created!"
echo ""
echo "Access Kibana at: http://your-server-ip:5601"
echo ""
echo "To view logs:"
echo "1. Go to Kibana → Discover"
echo "2. Select 'otel-logs*' index pattern"
echo "3. Search for logs with trace_id, span_id, or severity_text"
echo ""
echo "Example queries:"
echo "  - severity_text:ERROR"
echo "  - trace_id:* (logs with trace correlation)"
echo "  - message:\"MySQL connection\""
echo ""
