# Database Overview

Status: Approved specification materialization  
Source: `ERD.pdf`, `CNTT_KLCN101_Tran Van Tho.md`, `Ket_Qua_Khao_Sat_Bai_Xe.md`, `backend/pom.xml`, `backend/src/`  
Implementation status: ERD has not been implemented in backend; current `application.yaml` contains only the application name.

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
    -> JPA model
    -> controlled Migration
    -> MySQL schema
    -> schema validation
    -> ERD / data documentation synchronization
```

Code First là hướng phát triển. Entity/Migration được tạo khi feature được triển khai; tài liệu này không tạo Java, SQL hoặc migration. ERD không được phát triển như nguồn SQL độc lập và không được sửa âm thầm. Nếu cần điều chỉnh ERD, phải có phê duyệt của nhóm trước khi thay đổi implementation/source.

## Constraints and Metadata

Data dictionary chỉ ghi type và field/table được thể hiện trong ERD. Nullability, default, UNIQUE, index, cascade và các constraint không đọc thấy rõ được ghi **Not specified by approved ERD**; không suy ra từ tên cột hoặc quy tắc ORM.
