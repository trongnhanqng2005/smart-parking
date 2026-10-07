# Backend Authentication and RBAC

Status: Implemented backend capability; the authentication and schema decisions in this document are project-approved. The AHR permission catalog and AHR-03 Apartment, AHR-04 Resident profile, AHR-05 Household Membership, AHR-06 household-head/Apartment lifecycle, AHR-07 Vehicle lookup/OWNER, AHR-08 AUTHORIZED_USER grant/query, AHR-09 VehicleRight lifecycle/guarantor-loss, AHR-10 Resident status, and AHR-11 Membership/VehicleRight VOID API subsets are implemented; other business workflows remain deferred.

## Purpose and business context

This capability authenticates internal Smart Parking operators and applies one server-side identity and permission model to the Thymeleaf Web application and external REST clients such as the future WinForms client, with channel-specific authentication boundaries. Web authentication is for MANAGEMENT; REST authentication supports MANAGEMENT and GATE_STAFF. Its security boundary establishes **who** made a request and what assigned permission/context the user has. A JWT, role or AI result never decides whether a vehicle may cross a gate.

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
| Authenticate and authenticated logout | Web and REST, if ACTIVE | REST/WinForms only, if ACTIVE; Web login denied | Yes; Web is MANAGEMENT-only |
| Change own password | Yes, with current password | Yes, with current password | Yes; `SECURITY_CHANGE_OWN_PASSWORD` is assigned to both roles |
| Operate gate/shift | Only with the management user assigned to an open shift for the requested lane | Only with the user assigned to an open shift for the requested lane | Shared `ShiftAuthorizationService` boundary only; no gate endpoint |
| Review management-required exceptions / resolve offline conflicts | Management authority is required, in addition to the relevant workflow conditions | No | Future NV03–NV07 capability; no permission seeded |
| Manage resident profiles and household/vehicle rights | Within the AHR permission catalog and its approved workflow | No AHR permissions | AHR-02 seeds eight MANAGEMENT-only permissions; AHR-03 implements the Apartment subset, AHR-04 the Resident profile subset, AHR-05 Membership lifecycle, AHR-06 household-head transfer/Apartment status, AHR-07 existing Vehicle lookup/OWNER assignment/transfer, AHR-08 AUTHORIZED_USER grant/query, AHR-09 VehicleRight lifecycle/guarantor-loss integration, AHR-10 Resident status and AHR-11 Membership/VehicleRight VOID |
| Manage pricing, payments, reports, media or audit | Within a specific approved permission and workflow | Only the shift-limited task/data specifically authorized | Future NV01–NV08 capability; no permissions or endpoints seeded for these capabilities |
| Administer user accounts or role/permission assignments | Highest role, subject to future management APIs | No | Account/role administration endpoints are not part of this auth slice |

Possessing `MANAGEMENT` does not bypass business safety or shift checks. The permission catalog contains `SECURITY_CHANGE_OWN_PASSWORD` for both roles and the eight AHR permissions for MANAGEMENT only. Each explicit AHR operation requires its source permission. Under the accepted AHR review-remediation decision, automatically triggered dependent effects inherit the source command's permission; only explicitly submitted nested Membership or VehicleRight actions require the corresponding additional manage permission. Only ACTIVE accounts can authenticate or perform protected authenticated operations. Other future permission codes are added with their relevant capability rather than seeded in advance.

## Web session and REST JWT architecture

Both channels use the same `users`, `user_roles`, `roles`, `role_permissions` and `permissions` data, `BCryptPasswordEncoder(12)`, account-status check and permission resolution.

### Thymeleaf Web

- Spring Security form processing uses `POST /login`; `POST /logout` is Spring Security logout. An authenticated logout invalidates the current Web session and records `AUTH_LOGOUT` when the actor is attributable. An anonymous or already-logged-out request is an idempotent no-op that returns `204` and does not create an `AUTH_LOGOUT` audit record. ACTIVE status is required for protected authenticated operations, not for this anonymous logout no-op. Current server-rendered page routes are listed below.
- Only ACTIVE users assigned the `MANAGEMENT` business role may complete Web login. A `GATE_STAFF` account is denied Web authentication even when its submitted credentials are valid. The denial has the same generic public `401` response as other authentication failures and does not reveal account existence, role or credential validity.
- A valid GATE_STAFF Web attempt is a failed Web-channel authentication attempt. It counts against the existing `(canonical username, servlet remote source IP)` throttle and does not clear prior failures. The existing threshold and `429` behavior apply.
- The Web-role decision occurs before successful-login side effects. A role-denied Web attempt does not record `AUTH_LOGIN_SUCCESS`, update `last_login_at`, establish an authenticated Web session or perform throttle-success handling. It is recorded internally as `AUTH_LOGIN_FAILURE` with a denial reason in the existing audit data; no new audit action or database enum is introduced.
- Authentication is kept in the servlet HTTP session; successful login changes the session ID. Spring Security CSRF protection stays enabled for Web state-changing requests, including login, logout and password change.
- Session idle timeout is 30 minutes. A security filter independently enforces an 8-hour absolute limit from successful authentication even if the session remains active.
- Session cookie configuration is `HttpOnly`, `Secure` and `SameSite=Lax`.
- Every authenticated Web request reloads current status, role, permissions and credential-change timestamp. Non-ACTIVE users or sessions predating a password change are rejected; permission changes take effect on the next request.

### REST / WinForms

- `POST /api/auth/login` continues to support ACTIVE MANAGEMENT and GATE_STAFF accounts. Denial of a GATE_STAFF Web attempt does not change REST/WinForms eligibility or REST login behavior.
- REST requests under `/api/**` are stateless and authenticate with `Authorization: Bearer <access_token>`; CSRF is disabled only on this stateless chain.
- Requests under `/api/management/**` require the `MANAGEMENT` role in addition to Bearer authentication. Each explicit AHR operation must also require its matching source permission from current database grants. AHR-02 established the catalog and boundary; AHR-03 implements the Apartment subset, AHR-04 the Resident subset, AHR-05 the Household Membership subset, AHR-06 the household-head/Apartment lifecycle subset, AHR-07 the existing Vehicle/OWNER subset, AHR-08 the AUTHORIZED_USER grant/query subset, AHR-09 the VehicleRight lifecycle subset, AHR-10 Resident status and AHR-11 Membership/VehicleRight VOID. Explicit nested effects require their owner-module manage permission; automatically triggered dependent effects inherit the source command permission.
- Login issues a signed HS256 JWT with a 12-hour default lifetime configurable through `smart-parking.security.jwt.access-token-ttl`.
- Claims are `iss=smart-parking`, `aud=smart-parking-api`, `sub=<users.id>`, `iat`, `exp`, `jti` and `cv` (the credential-change version). The token has no role, permission or shift claims.
- The server pins HS256 and validates signature, issuer, audience and timestamps without expiry leeway. For every validly signed token, the current ACTIVE account and its current RBAC grants are loaded server-side; `cv` must match the current `credential_changed_at` value.
- WinForms and Web traffic must use HTTPS in deployment; the backend does not treat a JWT as protection for cleartext transport.
- The HMAC key is the raw UTF-8 value of environment variable `SMART_PARKING_JWT_SECRET`; it must contain at least 32 bytes. Missing/short values fail startup. There is no refresh token, blacklist or key-overlap framework. Changing the secret invalidates old JWTs.
- REST logout returns success so the client can discard its token; it does not revoke a copied token. Password change advances the database credential timestamp, invalidating previously issued JWTs. A new login is required.

## Credential, username and throttling rules

- Usernames are trimmed and lowercased with locale-independent lowercase normalization before validation, persistence, lookup and throttling. A canonical username must be non-empty and contain no more than 100 Unicode code points. Uniqueness applies to the canonical `users.username`, and database uniqueness is the final guard against concurrent duplicate creation.
- New passwords must contain at least 10 Unicode code points and no more than 72 UTF-8 bytes. Password change requires the current password, and the new password must differ from it. Login/password inputs are bounded at the request boundary; hashes are salted BCrypt cost 12 and are never returned or audited.
- Failed login attempts are counted in memory by `(canonical username, servlet remote source IP)`. Five failures within a rolling 15-minute window cause subsequent requests for that pair to receive HTTP 429 until the window expires. Successful authentication in its channel clears that pair’s counter. A valid GATE_STAFF credential denied by Web-channel policy counts as a Web failure and does not clear the counter. Throttling does not alter `users.status`.
- The single-instance limiter retains at most 10,000 active pair keys and five timestamps per key. At capacity, unseen pairs are rejected until expired entries are reclaimed; tracked pairs are not evicted early.
- Failure messages do not disclose whether a username exists. The current single-instance in-memory limiter is not shared across multiple backend instances.

## Initial roles and MANAGEMENT bootstrap

At startup, the backend idempotently creates exactly the `MANAGEMENT` and `GATE_STAFF` role records, `SECURITY_CHANGE_OWN_PASSWORD` with grants to both roles, and these AHR permissions with grants to MANAGEMENT only:

| Permission | Resource | Action |
|---|---|---|
| `APARTMENT_READ` | `APARTMENT` | `READ` |
| `APARTMENT_MANAGE` | `APARTMENT` | `MANAGE` |
| `RESIDENT_READ` | `RESIDENT` | `READ` |
| `RESIDENT_MANAGE` | `RESIDENT` | `MANAGE` |
| `HOUSEHOLD_MEMBERSHIP_READ` | `HOUSEHOLD_MEMBERSHIP` | `READ` |
| `HOUSEHOLD_MEMBERSHIP_MANAGE` | `HOUSEHOLD_MEMBERSHIP` | `MANAGE` |
| `VEHICLE_RIGHT_READ` | `VEHICLE_RIGHT` | `READ` |
| `VEHICLE_RIGHT_MANAGE` | `VEHICLE_RIGHT` | `MANAGE` |

`GATE_STAFF` receives none of those eight permissions. Permission authorities are resolved from current database grants for each REST Bearer request and authenticated Web request. No other future NV business permissions are seeded.

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

A valid GATE_STAFF Web attempt is recorded as `AUTH_LOGIN_FAILURE` with an internal `WEB_CHANNEL_ROLE_DENIED` reason in the existing audit data. It does not produce `AUTH_LOGIN_SUCCESS` or update `last_login_at`; this uses the existing audit action and requires no new event value or database enum. Public login errors remain generic.

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

This REST login supports ACTIVE MANAGEMENT and GATE_STAFF accounts. A GATE_STAFF account denied by Web-channel policy remains eligible for REST/WinForms authentication.

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

### Apartment management — AHR-03

All routes require Bearer authentication, the `MANAGEMENT` role, and the listed current database permission. Request and response JSON uses `snake_case`.

| Method/path | Permission | Contract |
|---|---|---|
| `GET /api/management/apartments` | `APARTMENT_READ` | Optional exact `building` and `apartment_code` filters use the AHR-01 canonical keys; `page` defaults to 0, `size` defaults to 20 and is capped at 100. Returns `{items,page,size,total_items}` of `ApartmentSummary {id,building,apartment_code,floor_no,status}`, ordered by `created_at` descending then `id` descending. |
| `POST /api/management/apartments` | `APARTMENT_MANAGE` | `{building,apartment_code,floor_no?}`; server assigns ID, timestamps and `ACTIVE`. Returns `ApartmentDetail {id,building,apartment_code,floor_no,status,created_at,updated_at}` with 201. A duplicate canonical identity returns 409 `APARTMENT_IDENTITY_CONFLICT`. |
| `GET /api/management/apartments/{id}` | `APARTMENT_READ` | Returns `ApartmentDetail`; the detail read is audited. |
| `GET /api/management/apartments/{id}/history` | `APARTMENT_READ` | Uses the common zero-based pagination and ordering. Returns sanitized `HistoryItem {action,at,reason,actor_user_id,subject_id}`; it does not return audit snapshots. |
| `PATCH /api/management/apartments/{id}/correction` | `APARTMENT_MANAGE` | Optional `{building?,apartment_code?,floor_no?,reason?}`. Omitted fields remain unchanged; explicit `floor_no: null` clears the floor. An identity change is determined by the normalized key pair and requires a nonblank `reason`. Returns `ApartmentDetail`; duplicate identity returns 409 `APARTMENT_IDENTITY_CONFLICT`, and a lock conflict returns 409 `CONCURRENT_MODIFICATION`. |

Invalid input returns 400 `INVALID_REQUEST`; an unknown Apartment returns 404 `NOT_FOUND`; authentication and authorization retain the existing 401/403 behavior. Create and correction mutations are audited. Apartment list and history requests are not audited; successful detail reads are audited without recording response snapshots. These routes do not implement Apartment status changes or other NV01 workflows.

### Resident management — AHR-04

All routes require Bearer authentication, the `MANAGEMENT` role, and the listed current database permission. Request and response JSON uses `snake_case`.

| Method/path | Permission | Contract |
|---|---|---|
| `GET /api/management/residents` | `RESIDENT_READ` | Optional `full_name` partial-contains filter; `page` defaults to 0, `size` defaults to 20 and is capped at 100. Returns `{items,page,size,total_items}` of `ResidentSummary {id,full_name,status}`, ordered by `created_at` descending then `id` descending. The list omits identity number, date of birth, phone and email. |
| `POST /api/management/residents` | `RESIDENT_MANAGE` | `{full_name,identity_number,date_of_birth?,phone?,email?}`. A new Resident starts `ACTIVE` and returns `ResidentDetail` with 201. A matching normalized identity reuses the existing Resident without overwriting its profile and returns its detail with 200. |
| `GET /api/management/residents/{id}` | `RESIDENT_READ` | Returns `ResidentDetail {id,full_name,identity_number,date_of_birth,phone,email,status,created_at,updated_at}`; the sensitive detail read is audited. |
| `POST /api/management/residents/lookup` | `RESIDENT_READ` | Body `{identity_number}`; performs an exact normalized identity lookup without placing the identifier in the URL. Returns `ResidentDetail` with 200 or `NOT_FOUND` with 404. Exactly one sanitized request audit is recorded for each lookup, including not-found outcomes. |
| `GET /api/management/residents/{id}/history` | `RESIDENT_READ` | Uses the common zero-based pagination and ordering. Returns sanitized `HistoryItem {action,at,reason,actor_user_id,subject_id}`; it does not return audit snapshots. |
| `PATCH /api/management/residents/{id}/correction` | `RESIDENT_MANAGE` | Optional `{full_name?,identity_number?,date_of_birth?,phone?,email?,reason?}`. Omitted fields remain unchanged; explicit `null` clears `date_of_birth`, `phone` or `email`. Changing identity number requires a nonblank reason. Returns `ResidentDetail`; duplicate identity returns 409 `RESIDENT_IDENTITY_CONFLICT`, and a lock conflict returns 409 `CONCURRENT_MODIFICATION`. |

Invalid input returns 400 `INVALID_REQUEST`; an unknown Resident or exact lookup miss returns 404 `NOT_FOUND`; authentication and authorization retain the existing 401/403 behavior. A concurrent create whose identity cannot be resolved as a reuse returns 409 `CONCURRENT_MODIFICATION`. Resident creation/reuse, correction, successful detail reads and every exact identity lookup request are audited without full identity values or profile snapshots. Resident list and history requests are not audited. Resident status lifecycle is implemented separately under AHR-10; household Membership operations remain in AHR-05/AHR-06.

### Household Membership management — AHR-05

All routes require Bearer authentication, the `MANAGEMENT` role, and the listed current database permission. Membership responses contain relation identifiers and lifecycle fields only; they do not embed Resident profiles. List/history pagination uses the common zero-based contract, with stable descending `created_at` then `id` ordering.

| Method/path | Permission | Contract |
|---|---|---|
| `GET /api/management/memberships` | `HOUSEHOLD_MEMBERSHIP_READ` | Optional exact `apartment_id` and `resident_id` filters; `page` defaults to 0, `size` defaults to 20 and is capped at 100. Returns `{items,page,size,total_items}` of `MembershipDetail`. |
| `GET /api/management/memberships/{id}` | `HOUSEHOLD_MEMBERSHIP_READ` | Returns `MembershipDetail`; successful detail reads are audited. |
| `GET /api/management/memberships/{id}/history` | `HOUSEHOLD_MEMBERSHIP_READ` | Returns paged sanitized `HistoryItem {action,at,reason,actor_user_id,subject_id}`; audit snapshots are not returned. |
| `POST /api/management/memberships` | `HOUSEHOLD_MEMBERSHIP_MANAGE` | `{apartment_id,resident_id,member_role:MEMBER,valid_from,valid_to?,reason}`; creates an `ACTIVE` membership and returns `MembershipDetail` with 201. Apartment and Resident must be `ACTIVE`. |
| `POST /api/management/apartments/{id}/household-head/assign` | `HOUSEHOLD_MEMBERSHIP_MANAGE` | `{resident_id,valid_from,valid_to?,reason}`; creates an `ACTIVE` `HOUSEHOLD_HEAD` membership and returns `MembershipDetail` with 201. |
| `POST /api/management/memberships/{id}/end` | `HOUSEHOLD_MEMBERSHIP_MANAGE` | `{effective_at,reason}`; `valid_from < effective_at <= command time`, and if the membership already has `valid_to`, `effective_at` must not exceed it. Sets `INACTIVE`, stores `effective_at` in `valid_to`, and records the command time/reason in lifecycle metadata. |
| `POST /api/management/memberships/{id}/revoke` | `HOUSEHOLD_MEMBERSHIP_MANAGE` | Same `{effective_at,reason}` time bounds as END. Sets `REVOKED`, stores the effective end in `valid_to`, and records the command time/reason in lifecycle metadata. |
| `POST /api/management/memberships/{id}/void` | `HOUSEHOLD_MEMBERSHIP_MANAGE` | `{reason}`; marks a Membership created in error as `VOID`, preserving its interval and row, and returns `MembershipDetail` with 200. |

Intervals are half-open: `valid_from <= t < valid_to` when `valid_to` exists; a missing `valid_to` is unbounded. `valid_to` must be later than `valid_from`. `ACTIVE` memberships may be scheduled in the future. Overlapping `ACTIVE` memberships for the same Resident and Apartment return 409 `MEMBERSHIP_OVERLAP`; overlapping `HOUSEHOLD_HEAD` memberships in one Apartment return 409 `HOUSEHOLD_HEAD_CONFLICT`. Adjacent intervals are permitted. Inactive Apartment/Resident targets return 409 `STATUS_CONFLICT`; an invalid lifecycle state/time returns 409 `RELATION_STATE_CONFLICT`; unresolved lock/concurrency failures return 409 `CONCURRENT_MODIFICATION`. Unknown Apartment, Resident or Membership IDs return 404 `NOT_FOUND`; invalid input returns 400 `INVALID_REQUEST`.

Membership creation, household-head assignment, END, REVOKE and successful detail reads are audited with sanitized relation IDs/role/interval/reason data. List and history requests are not audited. END/REVOKE are immediate terminal commands; future `effective_at` values and pre-start REVOKE are not supported by this subset. Household-head transfer, Membership VOID and Apartment/Resident status changes remain outside AHR-05.

### Membership VOID — AHR-11

VOID requires Bearer authentication, the `MANAGEMENT` role and `HOUSEHOLD_MEMBERSHIP_MANAGE`. Request JSON is `snake_case`.

| Method/path | Request | Response/status |
|---|---|---|
| `POST /api/management/memberships/{id}/void` | `{reason}` | Updated `MembershipDetail`, 200 |

VOID is allowed from `ACTIVE`, `INACTIVE` and `REVOKED`; repeated VOID returns 409 `RELATION_STATE_CONFLICT`. The command preserves `valid_from` and `valid_to`, sets status `VOID`, and writes command time/reason to lifecycle metadata. Missing Membership returns 404 `NOT_FOUND`; blank/oversized reason or invalid ID returns 400 `INVALID_REQUEST`. The Membership and any provably dependent vehicle-right cascade/audits commit or roll back together.

### Household-head transfer and Apartment lifecycle — AHR-06

All routes require Bearer authentication, the `MANAGEMENT` role and the current listed permission. Request/response JSON is `snake_case`.

| Method/path | Permission | Contract |
|---|---|---|
| `POST /api/management/apartments/{id}/household-head/transfer` | `HOUSEHOLD_MEMBERSHIP_MANAGE` | `{from_membership_id,to_resident_id,effective_at,reason}`. Returns the new `MembershipDetail` with 200. The source must be the current effective household head, and the target Resident and Apartment must be ACTIVE. |
| `POST /api/management/apartments/{id}/deactivate` | `APARTMENT_MANAGE`, plus `HOUSEHOLD_MEMBERSHIP_MANAGE` when `membership_ends` is nonempty | `{reason,membership_ends?:[{membership_id,effective_at,reason}]}`. Returns `ApartmentDetail` with 200. If effective memberships exist, every one must be explicitly ended in this same transaction. |
| `POST /api/management/apartments/{id}/reactivate` | `APARTMENT_MANAGE` | `{reason}`. Returns `ApartmentDetail` with 200; prior ended/revoked Memberships are not restored. |

Household-head transfer permits a future `effective_at`: the old Membership remains `ACTIVE` with `valid_to=effective_at`, and the new `ACTIVE` Membership starts at the same instant. Their half-open intervals meet without overlap, and the effective-head predicate switches at that time. Both Membership histories are written in the same transaction. Apartment deactivation records the Apartment transition and any explicit Membership ENDs atomically. Guard/status conflicts return 409 `STATUS_CONFLICT`; invalid nested Membership state/effective time returns 409 `RELATION_STATE_CONFLICT`; overlap/head conflicts retain the AHR-05 codes; unresolved lock-set races return 409 `CONCURRENT_MODIFICATION`. All state-changing operations require a nonblank reason and are audited; no vehicle relations are changed in AHR-06.

### Existing Vehicle lookup and OWNER management — AHR-07

All routes require Bearer authentication, the `MANAGEMENT` role and the listed current database permission. Request/response JSON is `snake_case`.

| Method/path | Permission | Contract |
|---|---|---|
| `GET /api/management/vehicles` | `VEHICLE_RIGHT_READ` | Optional `plate_number` exact-matches `vehicles.plate_normalized`; callers supply the already normalized value and the backend does not transform it. `page` defaults to 0, `size` defaults to 20 and is capped at 100. Returns `{items,page,size,total_items}` of `VehicleSummary {id,plate_number,vehicle_category_id,brand,status}`, ordered by `created_at` descending then `id` descending. List results are not audited. |
| `GET /api/management/vehicles/{id}` | `VEHICLE_RIGHT_READ` | Returns the same minimized `VehicleSummary`; a successful detail read is audited. |
| `POST /api/management/vehicles/{id}/owner` | `VEHICLE_RIGHT_MANAGE` | `{resident_id,valid_from,valid_to?,reason}`; creates an ACTIVE OWNER relation on the existing Vehicle and returns `VehicleRightDetail` with 201. It never creates a Vehicle. |
| `POST /api/management/vehicles/{id}/owner/transfer` | `VEHICLE_RIGHT_MANAGE` | `{from_relation_id,to_resident_id,effective_at,reason}`; atomically ends the current effective OWNER interval and creates the successor OWNER, returning the new `VehicleRightDetail` with 200. |

OWNER assignment and transfer require a nonblank reason and audit relation changes with Vehicle/Resident IDs and interval metadata only. An INACTIVE Resident cannot receive an OWNER relation. A BLOCKED Resident may hold an OWNER relation, but BLOCKED use restrictions still apply; Vehicle status does not gate these commands. A future transfer keeps the old OWNER ACTIVE with `valid_to=effective_at` and creates the new ACTIVE OWNER with `valid_from=effective_at`; their half-open intervals switch at that instant. Assignment/transfer locks involved Residents in ascending ID order, then the Vehicle and overlapping relation rows, and revalidates the OWNER and Resident–Vehicle intervals before mutation.

Invalid input returns 400 `INVALID_REQUEST`; a missing Vehicle, Resident or source relation returns 404 `NOT_FOUND`. An overlapping OWNER returns 409 `VEHICLE_OWNER_CONFLICT`; overlapping relations for the same Resident–Vehicle pair return 409 `VEHICLE_RIGHT_OVERLAP`; an INACTIVE Resident returns 409 `STATUS_CONFLICT`; an invalid transfer source/time returns 409 `RELATION_STATE_CONFLICT`; unresolved lock failures return 409 `CONCURRENT_MODIFICATION`. List/detail and OWNER commands do not create Vehicle records or implement AUTHORIZED_USER guarantor/lifecycle behavior.

### AUTHORIZED_USER grant and VehicleRight queries — AHR-08

All routes require Bearer authentication, the `MANAGEMENT` role and the current listed permission. Relation responses contain IDs and lifecycle/guarantor fields only; they never embed Resident profiles.

| Method/path | Permission | Contract |
|---|---|---|
| `GET /api/management/vehicle-rights` | `VEHICLE_RIGHT_READ` | Optional exact `vehicle_id`, `resident_id`, and `relation_type` filters; common pagination/order. Returns paged `VehicleRightDetail`. List reads are not audited. |
| `GET /api/management/vehicle-rights/{id}` | `VEHICLE_RIGHT_READ` | Returns `VehicleRightDetail`; a successful detail read is audited. |
| `GET /api/management/vehicle-rights/{id}/history` | `VEHICLE_RIGHT_READ` | Returns paged sanitized `HistoryItem`; audit snapshots are not returned and history reads are not audited. |
| `POST /api/management/vehicle-rights` | `VEHICLE_RIGHT_MANAGE` | `{vehicle_id,resident_id,guarantor_type,guarantor_resident_id,guarantor_apartment_id?,valid_from,valid_to?,reason}`. Creates an `ACTIVE` `AUTHORIZED_USER` relation on an existing Vehicle and returns `VehicleRightDetail` with 201. The server controls relation type/status and lifecycle metadata. |

The guarantor parties and context are validated at the new grant's `valid_from`; an ACTIVE grant may be scheduled. For `guarantor_type=OWNER`, `guarantor_resident_id` must identify the effective OWNER at that instant and `guarantor_apartment_id` must be omitted. For `HOUSEHOLD_HEAD`, the named guarantor must have an effective `HOUSEHOLD_HEAD` membership in the supplied Apartment A, an effective OWNER must exist for the Vehicle, that OWNER Resident must have an effective membership in the same Apartment A, and Apartment A must be ACTIVE at `valid_from`. Later guarantor authority loss is handled by AHR-09. The target Resident and named guarantor must be ACTIVE; an existing Vehicle is required but its status does not gate this grant. Face verification is not required and no gate decision is made.

The entire requested grant interval must fit within every required guarantor source interval: the OWNER relation and, for `HOUSEHOLD_HEAD`, both the head Membership and the OWNER Membership in Apartment A. Because intervals are half-open, a grant `valid_to` equal to a source `valid_to` is allowed. A null/unbounded grant end is rejected when any required source interval is finite; the server does not truncate the request. An interval extending beyond a source returns 409 `GUARANTOR_CHAIN_CONFLICT`. This is an approved AHR review-remediation decision, not an original NV01/ERD rule.

The target Resident–Vehicle interval must not overlap any existing ACTIVE relation for that pair, regardless of relation type. Other active relation rows are also checked for overlapping cardinality rules. A grant failure leaves no relation or audit record. A successful grant writes `VEHICLE_AUTHORIZED_USER_GRANTED`; audit data includes the system actor separately from the business guarantor and does not copy Resident profile data or identity numbers.

Invalid input returns 400 `INVALID_REQUEST`; an unknown Vehicle, Resident, Apartment or VehicleRight returns 404 `NOT_FOUND`; an invalid guarantor chain returns 409 `GUARANTOR_CHAIN_CONFLICT`, a Resident/Apartment status restriction returns `STATUS_CONFLICT`, a duplicate Resident–Vehicle interval returns `VEHICLE_RIGHT_OVERLAP`, an existing overlapping OWNER returns `VEHICLE_OWNER_CONFLICT`, and unresolved lock-set changes return `CONCURRENT_MODIFICATION`. OWNER assignment/transfer and VehicleRight END/REVOKE/VOID are implemented in AHR-07/AHR-09/AHR-11 respectively.

### Guarantor-loss integration and VehicleRight lifecycle — AHR-09

All routes require Bearer authentication, the `MANAGEMENT` role and `VEHICLE_RIGHT_MANAGE`. The request/response JSON uses `snake_case`; responses remain the minimized `VehicleRightDetail` relation contract.

| Method/path | Request | Response/status |
|---|---|---|
| `POST /api/management/vehicle-rights/{id}/end` | `{effective_at,reason}` | Updated VehicleRight, 200 |
| `POST /api/management/vehicle-rights/{id}/revoke` | `{effective_at,reason}` | Updated VehicleRight, 200 |
| `POST /api/management/vehicle-rights/{id}/void` | `{reason}` | Updated VehicleRight, 200 |

Direct END/REVOKE require `valid_from < effective_at <= command_time`; future-effective commands and explicit pre-start REVOKE are rejected. END sets `INACTIVE`; REVOKE sets `REVOKED`; both store `effective_at` in `valid_to`, with command time and reason in lifecycle metadata. Explicit REVOKE is distinct from automatic guarantor-loss END.

The same transaction handles dependent AUTHORIZED_USER grants when guarantor authority is lost through membership END/REVOKE, household-head transfer, Apartment deactivation with explicit membership ENDs, OWNER transfer, or direct OWNER END/REVOKE. For immediate losses, at `T <= valid_from` a scheduled grant becomes `PRE_EFFECTIVE_CANCELLED` with no `valid_to`; at `T > valid_from` it becomes `INACTIVE` with `valid_to=T`. A future OWNER or household-head transfer instead keeps an applicable grant ACTIVE with `valid_to=T`, stores a durable pending transition and ends it through due-time processing at `T`. The interval cutoff prevents effectiveness at or after `T` while processing is delayed. Grants starting at or after `T` are immediately `PRE_EFFECTIVE_CANCELLED`. Automatic changes preserve original guarantor/context fields and use the source command actor/reason in the terminal audit.

`resident` declares the narrow `VehicleRightInvalidationPort`; the vehicle-owned implementation performs dependent relation writes and audits. Resident commands lock source Residents in ascending ID order and their Apartment before the port discovers/locks affected Vehicles and relation rows; existing Membership rows are locked after VehicleRight rows. Future household-head transfer calls the vehicle-owned deferred-effect operation through this port; no reverse dependency from `resident` to `vehicle` is introduced. OWNER lifecycle/transfer discovers and locks HOUSEHOLD_HEAD Apartment contexts before Vehicle rows, then locks relation rows before membership rows. Candidate discovery runs after source aggregate locks in a fresh read transaction; source Resident/Apartment locks stabilize that candidate set. The source command, invalidations, pending effects and audit records commit or roll back together.

Invalid input returns 400 `INVALID_REQUEST`; unknown VehicleRight IDs return 404 `NOT_FOUND`; an inactive relation or invalid effective time returns 409 `RELATION_STATE_CONFLICT`; unresolved lock failures return 409 `CONCURRENT_MODIFICATION`. Resident status management is implemented by AHR-10; AHR-11 VOID is documented below.

### Resident status lifecycle — AHR-10

The command requires Bearer authentication, the `MANAGEMENT` role and `RESIDENT_MANAGE`. Each nonempty nested action list additionally requires its owner permission. Request/response JSON is `snake_case`.

| Method/path | Permission | Contract |
|---|---|---|
| `POST /api/management/residents/{id}/status` | `RESIDENT_MANAGE`; plus `HOUSEHOLD_MEMBERSHIP_MANAGE` when `membership_actions` is nonempty and `VEHICLE_RIGHT_MANAGE` when `vehicle_right_actions` is nonempty | `{status,reason,membership_actions?:[{membership_id,action:END\|REVOKE,effective_at,reason}],vehicle_right_actions?:[{relation_id,action:END\|REVOKE,effective_at,reason}]}`. Returns `ResidentDetail` with 200. |

Authorization distinguishes explicit nested actions from automatic dependent effects. An authorized source status command (including a BLOCKED transition) may carry its automatic guarantor-loss effects under `RESIDENT_MANAGE` without requiring `VEHICLE_RIGHT_MANAGE` or `HOUSEHOLD_MEMBERSHIP_MANAGE` as an extra permission. A nonempty `membership_actions` list requires `HOUSEHOLD_MEMBERSHIP_MANAGE`; a nonempty `vehicle_right_actions` list requires `VEHICLE_RIGHT_MANAGE`. The same source-permission rule applies to automatic effects of household-head/OWNER transfer and other guarantor-loss commands. This AHR review-remediation clarification is not an original ERD/NV01 permission rule.

Only `ACTIVE → INACTIVE`, `INACTIVE → ACTIVE`, `ACTIVE → BLOCKED`, `BLOCKED → ACTIVE` and `BLOCKED → INACTIVE` are accepted. Invalid transitions and an INACTIVE guard failure return 409 `STATUS_CONFLICT`; invalid nested relation state/effective time returns 409 `RELATION_STATE_CONFLICT`; unresolved lock-set changes return 409 `CONCURRENT_MODIFICATION`. A referenced resource outside the target Resident's status effects returns 404 `NOT_FOUND`; malformed requests return 400 `INVALID_REQUEST`.

INACTIVE requires all currently effective Memberships, direct VehicleRights and unhandled effective guarantor dependencies to be explicitly handled in the same command. Nested lifecycle actions require `valid_from < effective_at <= command_time`, and cannot extend beyond an existing `valid_to`. BLOCKED preserves Membership and OWNER history, but removes the Resident's guarantor authority: dependent grants become `PRE_EFFECTIVE_CANCELLED` at `T <= valid_from`, or `INACTIVE` with `valid_to=T` at `T > valid_from`. Reactivation does not restore ended Memberships or VehicleRights. A BLOCKED Resident cannot be a new AUTHORIZED_USER recipient or guarantor; a blocked OWNER may retain an existing OWNER relation.

Resident, nested Membership/VehicleRight changes, automatic guarantor effects and their audits commit or roll back together. The transaction follows Resident → Apartment → Vehicle → relation lock order, revalidates discovered dependencies under lock and retries an expanded lock set from a fresh transaction when necessary. Only explicit nested actions receive their corresponding owner-module lifecycle audit; the final Resident status change is always audited with sanitized IDs/reasons, not a profile snapshot.

### Membership and VehicleRight VOID — AHR-11

VOID commands require Bearer authentication, the `MANAGEMENT` role and the resource's manage permission: `HOUSEHOLD_MEMBERSHIP_MANAGE` for Membership, `VEHICLE_RIGHT_MANAGE` for VehicleRight. Both bodies are `{reason}` and return the updated minimized relation detail with 200.

VOID is allowed from `ACTIVE`, `INACTIVE` or `REVOKED`, and rejects already-VOID rows. VehicleRight VOID also rejects `PRE_EFFECTIVE_CANCELLED`, which records a valid scheduled grant that never became effective. The command sets `status=VOID`, preserves `valid_from`/`valid_to` and all guarantor/context history, and stores command time/reason in lifecycle metadata. It does not delete the row or turn VOID into END/REVOKE. Blank/oversized reasons and invalid IDs return 400 `INVALID_REQUEST`; unknown resource IDs return 404 `NOT_FOUND`; invalid source state returns 409 `RELATION_STATE_CONFLICT`.

Voiding a Membership or OWNER invokes synchronous vehicle-owned cascading in the same transaction. Dependent AUTHORIZED_USER history is marked VOID, including `ACTIVE`, `INACTIVE`, `REVOKED` and `PRE_EFFECTIVE_CANCELLED`, when dependency on that source interval is provable from the stored guarantor/context and interval/lifecycle evidence. Ambiguous historical dependency is left unchanged rather than voiding a possibly unrelated relation. Every changed relation receives its owner-module VOID audit, and a failure rolls back the parent change, child changes and audits together. Direct AUTHORIZED_USER VOID does not affect its guarantor or sibling grants.

## Web interface

### Thymeleaf pages and shared shells

The `security` feature currently serves these server-rendered authentication-foundation pages:

| GET route | Access | Current page |
|---|---|---|
| `/login` | Public; an authenticated MANAGEMENT user is redirected to `/` | MANAGEMENT sign-in |
| `/` | MANAGEMENT only; anonymous document navigation redirects to `/login` | Authenticated username, MANAGEMENT role and Smart Parking Web context |
| `/account/security` | MANAGEMENT and `SECURITY_CHANGE_OWN_PASSWORD` required | Own-password change form |

The login page uses the public layout. The home and account-security pages use the authenticated layout; both shells share the head, header and theme control. Account-security and logout actions appear in the header only for authenticated users. These pages do not implement NV01–NV08 business workflows.

### Web form/session endpoints

- `POST /login`: form parameters `username`, `password`; CSRF token required; only ACTIVE MANAGEMENT accounts can succeed (`204`). All denied outcomes, including valid GATE_STAFF credentials, receive the same generic `401`; throttled attempts receive `429` under the existing contract. A valid GATE_STAFF attempt counts as a failure and does not clear the throttle or perform successful-login side effects.
- `POST /logout`: CSRF token required; invalidates the server session and returns `204`.
- `POST /web/auth/change-password`: authenticated session, CSRF token, JSON body matching REST password-change fields; success invalidates that session and returns `204`. Other sessions are rejected by credential-version comparison.

## Acceptance criteria

1. Web login accepts only ACTIVE MANAGEMENT accounts; REST login continues to accept ACTIVE MANAGEMENT and GATE_STAFF accounts. Both channels use current server-side permissions; non-ACTIVE, unassigned-role, expired, wrong-signature, wrong-issuer/audience or pre-password-change callers cannot access protected operations.
2. User creation/update paths canonicalize usernames; duplicate canonical usernames and multiple role assignments per user fail at the database boundary. Every scalar surrogate ID is DB-generated/JPA IDENTITY while the two RBAC join keys stay composite.
3. Web unsafe requests fail without CSRF; login rotates session ID; sessions expire after 30 minutes idle or 8 hours absolute; logout and password change invalidate the applicable session(s).
4. REST login returns the Bearer contract with 12-hour default TTL, no refresh token, no permission/shift claims; REST logout only tells the client to discard the token; changing the JWT secret invalidates old tokens.
5. BCrypt cost 12 is used; password minimum, 72-byte maximum, current-password check and changed-password invalidation are enforced; attempts throttle at five failures per normalized username/source-IP pair per 15 minutes without changing user status. Valid GATE_STAFF Web attempts count as failures, do not clear the throttle, receive a generic `401` before throttling, and may receive the existing `429` after the threshold.
6. Initial bootstrap creates only the two roles, the self-password permission/grants, and the eight AHR permissions/grants to MANAGEMENT only; it requires controlled credentials when no management identity exists and is safe to repeat without resetting credentials.
7. Login outcomes, logout, password change and bootstrap grant/account events are audited without secrets; failed unknown identities are not attributed to a real user. Role-denied Web login uses `AUTH_LOGIN_FAILURE` with an internal denial reason and never records `AUTH_LOGIN_SUCCESS`, updates `last_login_at` or establishes a successful Web session.
8. Gate context authorization requires the requesting MANAGEMENT or GATE_STAFF user to be assigned to the specified open shift and lane; it does not decide the gate outcome.
9. The AHR-03 Apartment, AHR-04 Resident, AHR-05 Household Membership, AHR-06 household-head/Apartment lifecycle, AHR-07 existing Vehicle/OWNER, AHR-08 AUTHORIZED_USER grant/query, AHR-09 VehicleRight lifecycle/guarantor-loss, AHR-10 Resident status and AHR-11 Membership/VehicleRight VOID API subsets require their source MANAGEMENT-only permissions, return minimized lookup contracts, preserve authorized history/cardinality invariants and atomic cross-module changes under concurrency, and audit detail reads and mutations without recording full identity values. Only explicit nested actions require additional owner-module manage permissions; automatic dependent effects inherit the source command permission.

## Deferred scope

No other NV01–NV08 business screens or endpoints beyond the AHR-03 Apartment, AHR-04 Resident profile, AHR-05 Household Membership, AHR-06 household-head/Apartment lifecycle, AHR-07 existing Vehicle lookup/OWNER assignment/transfer, AHR-08 AUTHORIZED_USER grant/query, AHR-09 VehicleRight END/REVOKE/guarantor-loss, AHR-10 Resident status and AHR-11 Membership/VehicleRight VOID subsets are included. Vehicle creation, user CRUD or role-assignment API, password recovery, refresh tokens, token blacklist, multi-key rotation, multi-instance throttle storage, trusted proxy/IP-forwarding policy, gate business decision or WinForms offline/synchronization implementation remain deferred. Offline actor changes remain historical evidence requiring NV07 review before side effects are replayed.
