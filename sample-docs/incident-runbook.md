# Orbital Dynamics Incident Response Runbook

## Severity levels

S1: Atlas is down for all customers. S2: a major feature is degraded for many customers. S3: a minor feature is affected or a workaround exists. S4: cosmetic issues with no customer impact.

## First response

For an S1 incident the on-call engineer assigns an incident commander within 10 minutes and the status page is updated within 20 minutes. For an S2 incident the status page is updated within 1 hour. If a page is not acknowledged it escalates automatically to the secondary on-call engineer.

## Rollback first

If a deployment happened in the last 2 hours, roll it back before investigating further. Rollbacks are performed with the deploy tool and do not need approval during an S1 or S2 incident.

## Communication

Customers are informed through the public status page. Internal updates go to the incident chat channel every 30 minutes during an S1 incident. Only the incident commander may speak to customers during an incident.

## After the incident

A blameless postmortem is published within 5 business days for every S1 and S2 incident. Follow-up actions are tracked as tickets with the prefix OPS- and reviewed in the weekly operations meeting.
