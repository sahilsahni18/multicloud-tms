# Security flow

Stateless JWT authentication with rotating refresh tokens. Code lives in `backend/src/main/java/com/trackflow/tms/security`.

## Tokens

| Token | Lifetime | Where it lives | Stored server-side |
|---|---|---|---|
| Access token (JWT, HS512) | 15 min | Browser memory (Redux), sent as `Authorization: Bearer` | No; validated by signature |
| Refresh token (256-bit random) | 7 days | `tf_refresh` cookie: httpOnly, SameSite=Strict, Path=/api/v1/auth, Secure outside local | SHA-256 hash in `refresh_tokens` |

JWT claims: `sub` (user id), `email`, `name`, `roles`, `iss=trackflow`, `iat`, `exp`, `jti`. Roles come from the token, so requests do not hit the database to authenticate; a disabled user keeps access for at most 15 minutes, and their next refresh is refused.

## Sequence

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser (React)
    participant F as Security filters
    participant A as AuthController / AuthService
    participant DB as MySQL

    B->>F: POST /api/v1/auth/login {email, password}
    F->>F: Rate limit (10/min/IP)
    F->>A: permitAll
    A->>DB: load user + roles, BCrypt verify
    A->>DB: insert refresh_tokens(hash, family)
    A-->>B: 200 {accessToken, expiresIn, user} + Set-Cookie tf_refresh

    B->>F: GET /api/v1/... Authorization: Bearer JWT
    F->>F: JwtAuthenticationFilter verifies signature, expiry, issuer
    F->>A: @PreAuthorize role check, then row-level policy (Step 3)
    A-->>B: 200

    Note over B,F: Access token expires after 15 min
    B->>F: GET ... (expired JWT)
    F-->>B: 401 problem+json "Access token has expired"
    B->>A: POST /api/v1/auth/refresh (cookie)
    A->>DB: lock token row; revoked? -> revoke family, 401
    A->>DB: revoke old, insert new (same family)
    A-->>B: 200 {accessToken} + Set-Cookie (rotated)
    B->>F: retry original request

    B->>A: POST /api/v1/auth/logout (cookie)
    A->>DB: revoke token
    A-->>B: 204 + cookie cleared
```

## Controls

| Threat | Control |
|---|---|
| Password theft from DB | BCrypt cost 12 |
| Refresh token theft from DB | Only SHA-256 hashes stored |
| Stolen refresh token replayed | Rotation + family revocation on reuse (logged as WARN) |
| XSS reading tokens | Refresh token is httpOnly; access token only in memory |
| CSRF | No cookie auth on business APIs; refresh cookie is SameSite=Strict and path-scoped |
| Brute force | 10 requests/min/IP on login, register, refresh (per pod) |
| User enumeration | Same 401 message for unknown email, wrong password, disabled account |
| Forged / expired / foreign JWT | Signature, expiry (30 s skew) and issuer checked on every request |
| Weak signing key | Startup fails unless the key is at least 512 bits |
| Spoofed client IP | `X-Forwarded-For` trusted only from internal proxy addresses |

## Error format

Every error is RFC 7807 `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Access token has expired",
  "instance": "/api/v1/auth/me",
  "timestamp": "2026-10-08T09:08:18.597Z",
  "correlationId": "110b6c1e-..."
}
```

Validation errors add `"errors": [{"field": "email", "message": "must be a well-formed email address"}]`. The `correlationId` is also returned in the `X-Correlation-Id` header and is printed on every log line for that request.
