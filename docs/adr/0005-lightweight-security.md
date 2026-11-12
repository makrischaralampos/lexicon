# 5. API-key filter and in-process rate limiter instead of Spring Security

Status: accepted

## Context

The API needs authentication and abuse protection, since LLM calls are expensive, but has no users, roles or
sessions.

## Decision

A servlet filter checks `X-Api-Key` against SHA-256 digests of the configured keys using constant-time
comparison; a second filter applies a per-client token bucket (client = key digest label or remote address) with a
stricter tier for the LLM endpoints. Both are small, injectable-clock classes covered by unit tests, and failures
are rendered as problem+json. Authentication is off, with a startup warning, when no keys are configured.

## Consequences

- Very little machinery for stateless machine-to-machine access, and no new dependencies.
- The rate limiter is per instance; a multi-instance deployment needs a shared store such as Redis.
- Spring Security becomes the right tool once OAuth/JWT, roles or CORS rules are needed.
