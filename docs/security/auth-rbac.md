# Backend Authentication and RBAC

Status: Implemented backend capability; the authentication and schema decisions in this document are project-approved. Business workflow permission catalogs remain deferred until their endpoints exist.

## Purpose and business context

This capability authenticates internal Smart Parking operators and applies one server-side identity and permission model to the Thymeleaf Web application and external REST clients such as the future WinForms client. Its security boundary establishes **who** made a request and what assigned permission/context the user has. A JWT, role or AI result never decides whether a vehicle may cross a gate.

The backend remains a Spring Boot modular monolith. `security` owns users and RBAC; `audit` owns action records; `gate` owns shifts and later gate authorization. See [backend architecture](../architecture/backend-architecture.md).

## Business context and workflow relationships

- [NV01 — resident and vehicle registration](../business/nv01-resident-vehicle-registration.md): distinguishes Ban quản lý from Nhân viên trạm gác, applies least privilege, limits staff visibility to shift needs and reserves profile edits to management.
- [NV02 — cards, subscriptions and payment](../business/nv02-card-subscription-payment.md): distinguishes management price/adjustment approvals from authorized staff card/payment work; payment work is associated with the staff shift.
- [NV03 — resident entry](../business/nv03-resident-entry.md), [NV04 — resident exit](../business/nv04-resident-exit.md), [NV05 — visitor parking](../business/nv05-visitor-parking.md): staff operate lanes within a shift; backend business rules, not authentication or AI inference, determine gate outcomes. This capability does not implement those workflows.
- [NV07 — incident and manual processing](../business/nv07-incident-manual-processing.md): manual decisions/actions must identify their actor; offline evidence remains queued, and missing offline data does not grant passage. Historical events made before an operator’s authority changes are preserved and require NV07 review before unresolved side effects are replayed.
- [NV08 — reporting and reconciliation](../business/nv08-reporting-reconciliation.md): staff access is shift-scoped; management reporting and shift confirmation remain future business capabilities.

## Actors, roles and permission boundaries

Exactly two business roles exist: `MANAGEMENT` and `GATE_STAFF`. A user has exactly one business role. `MANAGEMENT` is the highest role; `SUPER_ADMIN` and any other role are unsupported. Only `ACTIVE` accounts authenticate or retain access. The schema retains the existing `ACTIVE`, `LOCKED` and `DISABLED` user-status literals.

Role membership is resolved from `user_roles`; permissions are resolved from `role_permissions`. Both are checked against current database state for Web requests and each Bearer request. The JWT contains no role, permission or shift authority.

`MANAGEMENT` is the highest role and inherits the effective permissions granted to `GATE_STAFF`, while remaining assigned only the `MANAGEMENT` business role. This inheritance does not bypass resource context: management still needs its own assigned open shift for gate operations. `GATE_STAFF` does not inherit management permissions.

| Capability boundary | MANAGEMENT | GATE_STAFF | Implemented now |
|---|---|---|---|
| Authenticate and authenticated logout | Yes, if ACTIVE | Yes, if ACTIVE | Yes |
| Change own password | Yes, with current password | Yes, with current password | Yes; `SECURITY_CHANGE_OWN_PASSWORD` is assigned to both roles |
| Operate gate/shift | Only with the management user assigned to an open shift for the requested lane | Only with the user assigned to an open shift for the requested lane | Shared `ShiftAuthorizationService` boundary only; no gate endpoint |
| Review management-required exceptions / resolve offline conflicts | Management authority is required, in addition to the relevant workflow conditions | No | Future NV03–NV07 capability; no permission seeded |
| Manage resident profiles, pricing, payments, reports, media or audit | Within a specific approved permission and workflow | Only the shift-limited task/data specifically authorized | Future NV01–NV08 capability; no business permissions or endpoints seeded |
| Administer user accounts or role/permission assignments | Highest role, subject to future management APIs | No | Account/role administration endpoints are not part of this auth slice |

Possessing `MANAGEMENT` does not bypass business safety or shift checks. The current permission catalog contains only the self-password-change permission needed by this capability. Only ACTIVE accounts can authenticate or perform protected authenticated operations. Future permission codes are added with the relevant capability rather than seeded in advance.

## Web session and REST JWT architecture

Both channels use the same `users`, `user_roles`, `roles`, `role_permissions` and `permissions` data, `BCryptPasswordEncoder(12)`, account-status check and permission resolution.

### Thymeleaf Web

- Spring Security form processing uses `POST /login`; `POST /logout` is Spring Security logout. An authenticated logout invalidates the current Web session and records `AUTH_LOGOUT` when the actor is attributable. An anonymous or already-logged-out request is an idempotent no-op that returns `204` and does not create an `AUTH_LOGOUT` audit record. ACTIVE status is required for protected authenticated operations, not for this anonymous logout no-op. No Thymeleaf page or screen is implemented here.
- Authentication is kept in the servlet HTTP session; successful login changes the session ID. Spring Security CSRF protection stays enabled for Web state-changing requests, including login, logout and password change.
- Session idle timeout is 30 minutes. A security filter independently enforces an 8-hour absolute limit from successful authentication even if the session remains active.
- Session cookie configuration is `HttpOnly`, `Secure` and `SameSite=Lax`.
- Every authenticated Web request reloads current status, role, permissions and credential-change timestamp. Non-ACTIVE users or sessions predating a password change are rejected; permission changes take effect on the next request.

### REST / WinForms

- REST requests under `/api/**` are stateless and authenticate with `Authorization: Bearer <access_token>`; CSRF is disabled only on this stateless chain.
- Login issues a signed HS256 JWT with a 12-hour default lifetime configurable through `smart-parking.security.jwt.access-token-ttl`.
- Claims are `iss=smart-parking`, `aud=smart-parking-api`, `sub=<users.id>`, `iat`, `exp`, `jti` and `cv` (the credential-change version). The token has no role, permission or shift claims.
- The server pins HS256 and validates signature, issuer, audience and timestamps without expiry leeway. For every validly signed token, the current ACTIVE account and its current RBAC grants are loaded server-side; `cv` must match the current `credential_changed_at` value.
- WinForms and Web traffic must use HTTPS in deployment; the backend does not treat a JWT as protection for cleartext transport.
- The HMAC key is the raw UTF-8 value of environment variable `SMART_PARKING_JWT_SECRET`; it must contain at least 32 bytes. Missing/short values fail startup. There is no refresh token, blacklist or key-overlap framework. Changing the secret invalidates old JWTs.
- REST logout returns success so the client can discard its token; it does not revoke a copied token. Password change advances the database credential timestamp, invalidating previously issued JWTs. A new login is required.

## Credential, username and throttling rules

- Usernames are trimmed and lowercased with locale-independent lowercase normalization before validation, persistence, lookup and throttling. A canonical username must be non-empty and contain no more than 100 Unicode code points. Uniqueness applies to the canonical `users.username`, and database uniqueness is the final guard against concurrent duplicate creation.
- New passwords must contain at least 10 Unicode code points and no more than 72 UTF-8 bytes. Password change requires the current password, and the new password must differ from it. Login/password inputs are bounded at the request boundary; hashes are salted BCrypt cost 12 and are never returned or audited.
- Failed login attempts are counted in memory by `(canonical username, servlet remote source IP)`. Five failures within a rolling 15-minute window cause subsequent requests for that pair to receive HTTP 429 until the window expires. Success clears that pair’s counter. Throttling does not alter `users.status`.
- The single-instance limiter retains at most 10,000 active pair keys and five timestamps per key. At capacity, unseen pairs are rejected until expired entries are reclaimed; tracked pairs are not evicted early.
- Failure messages do not disclose whether a username exists. The current single-instance in-memory limiter is not shared across multiple backend instances.

## Initial roles and MANAGEMENT bootstrap

At startup, the backend idempotently creates exactly the `MANAGEMENT` and `GATE_STAFF` role records, the `SECURITY_CHANGE_OWN_PASSWORD` permission and grants that permission to both roles. It does not seed future NV business permissions.

If no management-role assignment exists, the initial ACTIVE management account must be supplied through local/environment configuration:

- `SMART_PARKING_BOOTSTRAP_MANAGEMENT_USERNAME`
- `SMART_PARKING_BOOTSTRAP_MANAGEMENT_PASSWORD`
- `SMART_PARKING_BOOTSTRAP_MANAGEMENT_FULL_NAME`

The username is canonicalized, the password must meet the stated policy and is immediately BCrypt-hashed. Runtime configuration has no default or reusable bootstrap password. Startup fails if an initial management account is needed but the bootstrap values are missing/invalid. Once a management account exists, supplying bootstrap credentials does not reset it.

To run the backend, configure `SMART_PARKING_JWT_SECRET` and datasource variables; controlled MANAGEMENT bootstrap credentials are required only when no management-role assignment exists. The full Spring context test uses test-only JWT/bootstrap values and its test-profile runner seeds the RBAC catalog without creating a MANAGEMENT account in the configured database. Focused policy and Web MVC tests do not need the local MySQL datasource. Never commit or log runtime secrets or local database credentials.

## Shift and offline boundary

`ShiftAuthorizationService.requireAssignedOpenShift(userId, shiftId, laneId)` is the shared server-side prerequisite for future gate operations. It requires the authenticated user—whether management or gate staff—to own that open shift on that lane. Caller-supplied JWT data cannot satisfy this check. Current NV03–NV05 gate endpoints do not exist in this change, so the helper does not itself authorize or decide a gate outcome.

An expired JWT cannot authenticate reconnection or authorize offline gate decisions. This backend capability does not implement the WinForms queue or NV07 sync protocol. Events created before an operator loses status, role or shift authority are historical evidence: retain them and require NV07 review before replaying unresolved side effects. Authentication failure must not be treated as permission to discard or silently reassign that evidence.

## Authentication and RBAC audit

Authentication events are stored through the existing `audit_logs` table and its `audit` owner:

- `AUTH_LOGIN_SUCCESS`, `AUTH_LOGIN_FAILURE`, `AUTH_LOGIN_THROTTLED`
- `AUTH_LOGOUT`
- `AUTH_PASSWORD_CHANGE`
- `RBAC_BOOTSTRAP_PERMISSION_GRANT`, `RBAC_BOOTSTRAP_MANAGEMENT`

Records include the known actor when attribution is reliable, action/entity reference, request ID (when supplied and within schema length), servlet remote IP and event time. Unknown-user and bad-credential failures have no fabricated actor; the attempted canonical username may be the authentication entity reference. Passwords, password hashes, Bearer tokens and signing secrets are excluded from audit data and responses. There is no runtime role-assignment API in this slice, so no runtime grant-change event can occur yet.

## Persistence and migration decisions

Flyway V1 and V2 remain immutable. New Flyway V3:

1. Preflights username collisions after canonical trim/lowercase and duplicate user-role assignments, then canonicalizes stored usernames and enforces non-null uniqueness.
2. Enforces unique non-null role and permission codes and at most one role assignment per user; `user_roles` keeps its composite `(user_id, role_id)` primary key, and `role_permissions` keeps `(role_id, permission_id)`.
3. Adds nullable `users.credential_changed_at DATETIME(6)` for JWT/session credential invalidation.
4. Changes all 36 single-column surrogate `BIGINT` IDs to MySQL `AUTO_INCREMENT`; their JPA entities use `GenerationType.IDENTITY`. The two composite RBAC IDs remain composite and are not generated.

Username or role-assignment collision preflight fails rather than renaming accounts or discarding assignments. Database updates must apply the migration, confirm Flyway history, and pass Hibernate `ddl-auto: validate` before deployment. See [database overview](../database/database-overview.md), [data dictionary](../database/data-dictionary.md) and [relationships](../database/relationships.md).

### V3 failure recovery

V3 disables `FOREIGN_KEY_CHECKS` for the generated-ID alterations and restores it at the end of the script. If V3 stops before its final statement, stop the application and restore checks on the same still-open migration connection before reusing it:

```sql
SELECT @@SESSION.FOREIGN_KEY_CHECKS;
SET SESSION FOREIGN_KEY_CHECKS = 1;
```

Do not use `SET GLOBAL` for this recovery. If the migration connection has closed, verify the value on a new connection before continuing. MySQL DDL may have partially completed before a migration error, so inspect both `flyway_schema_history` and the actual schema; do not run Flyway repair or retry V3 against a partially altered schema. Restore a known pre-V3 backup and rerun the migration, or have the partial schema reconciled to a consistent pre-V3 state first. V3 is immutable after application; corrections require an approved migration/versioning decision.

V3's stored-username expression is MySQL `LOWER(TRIM(username))`; runtime canonicalization uses Java `strip()` and `Locale.ROOT` lowercase. Their equivalence for all Unicode case and whitespace characters is not established. Preserve V3 as applied and resolve any broader normalization change through an approved forward-migration decision rather than silently changing username rules.

## REST contract

All request and response JSON uses the documented snake_case fields below. Missing/invalid Bearer authentication returns 401 with a Bearer challenge; authenticated but denied REST requests return 403. Login and current-password failures return generic 401 errors, request/password-policy validation returns 400, and throttled login returns 429.

### `POST /api/auth/login` — anonymous

Request:

```json
{"username":"gate.staff","password":"example-password"}
```

Success `200`:

```json
{"access_token":"<signed-jwt>","token_type":"Bearer","expires_in":43200}
```

Credential failure returns `401` with `{"code":"AUTHENTICATION_FAILED","message":"Authentication failed"}`. A throttled username/IP pair returns `429` with `code=LOGIN_THROTTLED` and the same generic message. Login success updates `last_login_at`.

### `POST /api/auth/logout` — Bearer required

Success `204 No Content`; client discards the token. Server does not maintain a blacklist.

### `POST /api/auth/change-password` — Bearer and permission required

Request:

```json
{"current_password":"old-password","new_password":"new-password"}
```

Success `204 No Content`. The current password is verified; the new value must differ and meet policy. Previously issued JWTs fail on the next request because their `cv` no longer matches. No replacement token is issued.

### Web form/session endpoints

- `POST /login`: form parameters `username`, `password`; CSRF token required; success `204`, generic failure `401` or throttled `429`.
- `POST /logout`: CSRF token required; invalidates the server session and returns `204`.
- `POST /web/auth/change-password`: authenticated session, CSRF token, JSON body matching REST password-change fields; success invalidates that session and returns `204`. Other sessions are rejected by credential-version comparison.

## Acceptance criteria

1. Both channels authenticate the same ACTIVE account and use current server-side permissions; non-ACTIVE, unassigned-role, expired, wrong-signature, wrong-issuer/audience or pre-password-change callers cannot access protected operations.
2. User creation/update paths canonicalize usernames; duplicate canonical usernames and multiple role assignments per user fail at the database boundary. Every scalar surrogate ID is DB-generated/JPA IDENTITY while the two RBAC join keys stay composite.
3. Web unsafe requests fail without CSRF; login rotates session ID; sessions expire after 30 minutes idle or 8 hours absolute; logout and password change invalidate the applicable session(s).
4. REST login returns the Bearer contract with 12-hour default TTL, no refresh token, no permission/shift claims; REST logout only tells the client to discard the token; changing the JWT secret invalidates old tokens.
5. BCrypt cost 12 is used; password minimum, 72-byte maximum, current-password check and changed-password invalidation are enforced; attempts throttle at five failures per normalized username/source-IP pair per 15 minutes without changing user status.
6. Initial bootstrap creates only the two roles and the implemented self-password permission/grants, requires controlled credentials when no management identity exists, and is safe to repeat without resetting credentials.
7. Login outcomes, logout, password change and bootstrap grant/account events are audited without secrets; failed unknown identities are not attributed to a real user.
8. Gate context authorization requires the requesting MANAGEMENT or GATE_STAFF user to be assigned to the specified open shift and lane; it does not decide the gate outcome.

## Deferred scope

No Thymeleaf screens, user CRUD or role-assignment API, password recovery, refresh tokens, token blacklist, multi-key rotation, multi-instance throttle storage, trusted proxy/IP-forwarding policy, NV01–NV08 business permission seeds/endpoints, gate business decision or WinForms offline/synchronization implementation are included. Offline actor changes remain historical evidence requiring NV07 review before side effects are replayed.
