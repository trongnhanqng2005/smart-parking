# Database Overview

Status: Approved specification materialization  
Source: `ERD.pdf`, `CNTT_KLCN101_Tran Van Tho.md`, `Ket_Qua_Khao_Sat_Bai_Xe.md`, `backend/pom.xml`, `backend/src/`  
Implementation status: The original 38 ERD tables and 38 ERD enum types are represented by feature-owned JPA entities/enums. Flyway V1–V5 are applied locally; V4 materializes AHR-01 identity-key and relation-lifecycle decisions, and V5 applies the approved AHR review-remediation guarantor constraint correction plus pending VehicleRight transition storage. AHR-11 VOID uses existing V4 statuses/lifecycle fields and required no schema migration. Hibernate schema validation succeeds against local MySQL `smart_parking`.

## Approved Initial Design

`ERD.pdf` là thiết kế database ban đầu đã duyệt. Bản Markdown này mô tả lại các concept, cột, FK và enum nhìn thấy trong ERD; không thêm cột, bảng, constraint, index hoặc lifecycle ngoài nguồn. Xem [data dictionary](data-dictionary.md), [relationships](relationships.md) và [enums](enums.md).

## Domain Groupings and Backend Ownership

| Domain grouping | ERD tables | Backend owner |
|---|---|---|
| Security/RBAC | `users`, `roles`, `permissions`, `user_roles`, `role_permissions` | `security` |
| Resident/Apartment | `apartments`, `residents`, `apartment_memberships`, `identity_verifications` | `resident` |
| Vehicle | `vehicle_families`, `vehicle_categories`, `vehicles`, `vehicle_resident_relations` | `vehicle` |
| Card | `cards`, `card_assignments` | `card` |
| Pricing | `pricing_versions`, `monthly_rates`, `visitor_period_rates`, `visitor_car_rate_rules` | `pricing` |
| Decision policy | `decision_policy_versions` | `gate` |
| Subscription | `parking_subscriptions` | `subscription` |
| Gate | `gate_stations`, `gate_lanes`, `work_shifts`, `gate_events`, `manual_reviews`, `barrier_actions` | `gate` |
| Parking | `parking_sessions` | `parking` |
| Media | `media_assets`, `resident_media`, `gate_event_media`, `face_templates` | `media` |
| AI | `ai_inference_results` | `ai` |
| Billing | `charges`, `charge_items`, `payments` | `billing` |
| Alert | `alerts` | `alert` |
| Audit | `audit_logs` | `audit` |
| Reporting | Không có bảng riêng được chỉ định trong ERD | `report` đọc dữ liệu từ owner modules |

Ownership là ranh giới package đề xuất theo kiến trúc dự án; không thay đổi bảng/quan hệ đã duyệt. Các boundary bắt buộc: Apartment/Resident, Vehicle/Authorization, Card/Assignment, Pricing/Subscription, Parking Session/Gate Event, AI Inference/Business Decision, Manual Review/Barrier Action, Charge/Payment, Audit Log/Alert là các concept riêng.

## Code First Workflow

```text
approved business specification
    -> owning features' JPA entities and enum mappings
    -> reviewed Flyway V1 baseline (38 approved tables)
    -> Flyway applies migration to MySQL
    -> Flyway migration validation and Hibernate schema validation
    -> ERD / data documentation synchronization
```

Code First là hướng phát triển: JPA entity và Java enum được đặt theo feature owner và khớp tên bảng, cột, quan hệ, kiểu dữ liệu, composite key và enum literals trong tài liệu ERD. Migration `V1__create_approved_schema.sql` tạo baseline đầy đủ; V2 áp dụng độ dài email cư dân 150 ký tự đã duyệt mà không sửa V1. V3 materializes the approved authentication/RBAC decisions documented in [auth-rbac](../security/auth-rbac.md): canonical unique usernames, credential-change timestamp, one role assignment per user, unique role/permission codes, and generated surrogate IDs. V4 is a Java-based Flyway migration that materializes the separately approved AHR-01 normalized identity keys, guarantor/context foreign keys, table-specific relation lifecycle statuses and lifecycle metadata; it does not alter the original ERD baseline or `card_assignments.status`. AHR-11 uses the existing Membership/VehicleRight `VOID` statuses and lifecycle metadata; it adds no table, column, enum literal or foreign key. V3 is additive to the ERD baseline; V1–V3 remain immutable. Enum được lưu theo literal string bằng MySQL `ENUM` và Hibernate `SqlTypes.ENUM`; các AHR-01 table-specific enums được ghi riêng, không đổi literal của `card_assignments`. Các association dùng owning-side `@ManyToOne`; hai bảng nối dùng `@EmbeddedId`/`@MapsId`. Hibernate chỉ kiểm tra schema với `ddl-auto: validate`, không tạo hoặc cập nhật schema. ERD không được phát triển như nguồn SQL độc lập và không được sửa âm thầm. Nếu cần điều chỉnh ERD, phải có phê duyệt của nhóm trước khi thay đổi implementation/source.

V4 canonical identity transforms are NFC-first and implemented once in Java: `building` trims/collapses whitespace then Unicode-uppercases with `Locale.ROOT` and NFC again; `apartment_code` trims then Unicode-uppercases with `Locale.ROOT` and NFC again; `identity_number` trims, removes internal whitespace, uppercases Latin letters only, then NFC again. All keys are UTF-8 bytes in full-value unique `VARBINARY` indexes; MySQL performs binary comparison only. No NFKC or compatibility folding is applied.

V5/AHRR-01 tightens the vehicle guarantor CHECK so AUTHORIZED_USER requires `guarantor_type`, and adds a unique, vehicle-owned pending VehicleRight transition record for future-dated lifecycle effects. These are approved follow-up persistence decisions, not original ERD fields.

### Local MySQL setup

Chạy MySQL bằng Laragon và tạo database trước khi chạy backend. Ứng dụng không tự tạo database; tài khoản datasource cần quyền kết nối và quyền DDL cần thiết để Flyway áp dụng migration trong database đó.

```sql
CREATE DATABASE IF NOT EXISTS smart_parking;
```

Dùng một MySQL account local có quyền trên riêng database này. Flyway và ứng dụng dùng chung datasource, nên account cần quyền đọc/ghi runtime cùng quyền DDL để chạy migrations:

```sql
CREATE USER 'smart_parking_app'@'localhost' IDENTIFIED BY '<choose-a-local-password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON smart_parking.* TO 'smart_parking_app'@'localhost';
```

Thay `<choose-a-local-password>` bằng password local do developer quản lý. Đặt các biến môi trường trong terminal dùng để chạy backend. URL dưới đây dùng Laragon MySQL trên `127.0.0.1:3306`. Spring Boot đọc các biến môi trường này trực tiếp; không commit credentials hoặc file môi trường có credentials.

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/smart_parking"
$env:SPRING_DATASOURCE_USERNAME = "smart_parking_app"
$env:SPRING_DATASOURCE_PASSWORD = "<local-mysql-password>"
```

Chạy lệnh từ `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Thiếu URL, username hoặc password environment variable sẽ làm cấu hình datasource thất bại thay vì chọn một database mặc định. Dùng credentials của tài khoản MySQL local đã cấu hình để kết nối `smart_parking`.

### Flyway migration convention

- Location: `backend/src/main/resources/db/migration/` (`classpath:db/migration`).
- `V1__create_approved_schema.sql` là baseline chứa đủ 38 bảng đã duyệt; không tạo migration rỗng.
- V2 đã sửa `residents.email` theo độ dài 150 ký tự được duyệt. V3 materializes the approved auth/RBAC additions in `V3__authentication_identity_and_generated_ids.sql`. V4 `V4__apartment_resident_normalized_identity_and_lifecycle.java` is the Java-based AHR-01 migration; its custom Flyway checksum pins the migration revision/DDL definition. V5 `V5__vehicle_right_pending_transitions.java` is a checksummed AHR review-remediation migration. Coordinate later migration versions across concurrent feature branches.
- Migration đã áp dụng là bất biến. Thay đổi schema tiếp theo phải dùng version mới; không sửa migration cũ. Lịch sử Flyway trên database là căn cứ phiên bản đã áp dụng.
- V4 and V5 use non-transactional Java migrations because MySQL DDL implicitly commits. Their preflights run before DDL and fail closed on unsupported legacy rows/collisions. After either migration fails, inspect `flyway_schema_history` and actual schema, reconcile to a consistent state before Flyway repair/retry, and never retry blindly.
- Flyway áp dụng và kiểm tra migration trước khi Hibernate kiểm tra JPA mapping bằng `ddl-auto: validate`. Không dùng `schema.sql`/`data.sql` hay Hibernate để tạo schema.
- PK, hai composite PK và các FK bám theo `relationships.md`. MySQL/InnoDB tự tạo supporting index nếu FK chưa có index phù hợp; không thêm secondary index khác không được nguồn duyệt.
- Duy trì đồng bộ các thay đổi được duyệt giữa JPA, migration, MySQL schema và data dictionary/relationships/enums. Không tự bổ sung hoặc đổi concept, constraint hay cardinality chưa được ERD/nguồn duyệt xác định.

`V1` hiện vật hóa toàn bộ initial ERD baseline; V2 chỉ sửa độ dài `residents.email`; V3 is the approved auth/RBAC migration and remains immutable after application. Subsequent features use a new migration version. The approved V3 additions are limited to the authentication/RBAC contract and IDENTITY generation convention documented in [auth-rbac](../security/auth-rbac.md); other constraints remain unspecified unless separately approved.

## Constraints and Metadata

Data dictionary ghi type và field/table được thể hiện trong ERD, kèm các auth/RBAC V3 additions đã được duyệt. Nullability, default, UNIQUE, index, cascade và các constraint không đọc thấy rõ được ghi **Not specified by approved ERD** nếu không được V3 hoặc quyết định capability cụ thể xác định.
