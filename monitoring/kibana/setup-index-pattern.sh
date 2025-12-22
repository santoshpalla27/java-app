#!/bin/bash
# Kibana Index Pattern and Saved Search Auto-Provisioning Script

KIBANA_URL="http://kibana:5601"
ELASTICSEARCH_URL="http://elasticsearch:9200"

echo "Waiting for Kibana to be ready..."
until curl -s "$KIBANA_URL/api/status" | grep -q '"level":"available"'; do
  echo "Kibana not ready yet, waiting..."
  sleep 5
done

echo "Kibana is ready! Creating index pattern..."

# Create index pattern for otel-logs
curl -X POST "$KIBANA_URL/api/saved_objects/index-pattern/otel-logs" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
    "attributes": {
      "title": "otel-logs",
      "timeFieldName": "Body.@timestamp"
    }
  }'

echo -e "\n\nCreating saved search for error logs..."

# Create saved search for error logs
curl -X POST "$KIBANA_URL/api/saved_objects/search" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
    "attributes": {
      "title": "Error Logs",
      "description": "All ERROR level logs",
      "columns": ["Body.@timestamp", "Body.level", "Body.logger_name", "Body.message", "Body.trace_id"],
      "sort": [["Body.@timestamp", "desc"]],
      "kibanaSavedObjectMeta": {
        "searchSourceJSON": "{\"index\":\"otel-logs\",\"query\":{\"query\":\"Body.level:ERROR\",\"language\":\"lucene\"},\"filter\":[]}"
      }
    },
    "references": [
      {
        "id": "otel-logs",
        "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
        "type": "index-pattern"
      }
    ]
  }'

echo -e "\n\nCreating saved search for trace correlation..."

# Create saved search template for trace correlation
curl -X POST "$KIBANA_URL/api/saved_objects/search" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
    "attributes": {
      "title": "Trace Correlation (Template)",
      "description": "Search logs by trace_id - edit query to add specific trace_id",
      "columns": ["Body.@timestamp", "Body.level", "Body.logger_name", "Body.message", "Body.span_id"],
      "sort": [["Body.@timestamp", "asc"]],
      "kibanaSavedObjectMeta": {
        "searchSourceJSON": "{\"index\":\"otel-logs\",\"query\":{\"query\":\"Body.trace_id:*\",\"language\":\"lucene\"},\"filter\":[]}"
      }
    },
    "references": [
      {
        "id": "otel-logs",
        "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
        "type": "index-pattern"
      }
    ]
  }'

echo -e "\n\n✅ Kibana auto-provisioning complete!"
echo "Index pattern 'otel-logs' created"
echo "Saved searches created:"
echo "  - Error Logs"
echo "  - Trace Correlation (Template)"
