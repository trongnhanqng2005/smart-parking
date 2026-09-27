# Database Overview

Status: Approved specification materialization  
Source: `ERD.pdf`, `CNTT_KLCN101_Tran Van Tho.md`, `Ket_Qua_Khao_Sat_Bai_Xe.md`, `backend/pom.xml`, `backend/src/`  
Implementation status: The 38 feature-owned JPA entities and 38 Java enums are mapped to the approved ERD. Flyway V1 and the resident email width correction in V2 have been applied to the Laragon MySQL `smart_parking` database, and Hibernate schema validation succeeds.

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

Code First là hướng phát triển: JPA entity và Java enum được đặt theo feature owner và khớp tên bảng, cột, quan hệ, kiểu dữ liệu, composite key và enum literals trong tài liệu ERD. Migration `V1__create_approved_schema.sql` tạo baseline đầy đủ; V2 áp dụng độ dài email cư dân 150 ký tự đã duyệt mà không sửa V1. Enum được lưu theo literal string bằng MySQL `ENUM` và Hibernate `SqlTypes.ENUM`; không đổi literal. Các association dùng owning-side `@ManyToOne`; hai bảng nối dùng `@EmbeddedId`/`@MapsId`. Hibernate chỉ kiểm tra schema với `ddl-auto: validate`, không tạo hoặc cập nhật schema. ERD không được phát triển như nguồn SQL độc lập và không được sửa âm thầm. Nếu cần điều chỉnh ERD, phải có phê duyệt của nhóm trước khi thay đổi implementation/source.

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
- V2 đã sửa `residents.email` theo độ dài 150 ký tự được duyệt. Đặt các thay đổi tiếp theo theo `V<VERSION>__<description>.sql`, bắt đầu từ `V3`; phối hợp version giữa các feature branch đang phát triển đồng thời.
- Migration đã áp dụng là bất biến. Thay đổi schema tiếp theo phải dùng version mới; không sửa migration cũ. Lịch sử Flyway trên database là căn cứ phiên bản đã áp dụng.
- Flyway áp dụng và kiểm tra migration trước khi Hibernate kiểm tra JPA mapping bằng `ddl-auto: validate`. Không dùng `schema.sql`/`data.sql` hay Hibernate để tạo schema.
- PK, hai composite PK và các FK bám theo `relationships.md`. MySQL/InnoDB tự tạo supporting index nếu FK chưa có index phù hợp; không thêm secondary index khác không được nguồn duyệt.
- Duy trì đồng bộ các thay đổi được duyệt giữa JPA, migration, MySQL schema và data dictionary/relationships/enums. Không tự bổ sung hoặc đổi concept, constraint hay cardinality chưa được ERD/nguồn duyệt xác định.

`V1` hiện vật hóa toàn bộ initial ERD baseline; V2 chỉ sửa độ dài `residents.email`. Các feature branch sau chỉ thay đổi schema bằng version tiếp theo cho capability được duyệt. Không thêm nullability, default, UNIQUE, cascade, check constraint hoặc index ngoài PK/FK và supporting index MySQL yêu cầu.

## Constraints and Metadata

Data dictionary chỉ ghi type và field/table được thể hiện trong ERD. Nullability, default, UNIQUE, index, cascade và các constraint không đọc thấy rõ được ghi **Not specified by approved ERD**; không suy ra từ tên cột hoặc quy tắc ORM.
