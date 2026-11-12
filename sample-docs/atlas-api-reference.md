# Atlas API Reference

## Authentication

Every request must send an API key in the X-Api-Key header. When a key is rotated from the dashboard, the previous key stays valid for 24 hours so integrations can switch over without downtime.

## Limits and pagination

The sandbox environment allows 60 requests per minute per API key. List endpoints return 100 items per page by default and at most 500 items per page. Use the next_cursor field of a response to fetch the following page.

## Error codes

E-4101: the vessel identifier is unknown or has been deactivated.
E-4103: the report payload is larger than 64 KB and was rejected.
E-5001: the ingestion service is temporarily unavailable; retry with exponential backoff.
HTTP 401 is returned when the API key is missing or invalid.

## Webhooks

Arrival-estimate webhooks are delivered with an HMAC-SHA256 signature in the X-Atlas-Signature header. Failed deliveries are retried 5 times with exponential backoff before the webhook is disabled.

## Versions

Version 2 of the API is current. Version 1 is deprecated and will be removed on 31 March 2027. The latest position of a vessel is available at GET /v2/vessels/{imo}/position.
