# ERD Relationships

Status: Approved specification materialization  
Source: `ERD.pdf` — pages 1–2 (tables and relationship diagram); `CNTT_KLCN101_Tran Van Tho.md`; `Ket_Qua_Khao_Sat_Bai_Xe.md`  
Implementation status: The original 71 ERD foreign keys are mapped by owning-side JPA associations and declared in V1; they are applied to local MySQL. V4 adds the two approved AHR-01 guarantor/context foreign keys, and V5 adds pending-transition references to VehicleRight and source actor, also applied locally. AHR-11 VOID preserves these relationships and adds no FK.

## Reading Rules

- Mỗi dòng ghi FK field được thể hiện trong ERD và bảng target.
- Không suy diễn 1:1, nullable, UNIQUE, cascade hoặc chính sách xóa từ FK name. Các cardinality đặc biệt chỉ được mô tả ở mức association concept khi nguồn business nêu trực tiếp.
- Không có Mermaid ERD riêng trong Markdown để tránh tạo sơ đồ thứ hai khác với `ERD.pdf`.

## Security / RBAC

| Source table.field | Target | Semantic relationship | Workflow |
|---|---|---|---|
| `user_roles.user_id` | `users.id` | Association user–role; V3 unique key permits at most one role per user | Đăng nhập/RBAC, mọi NV |
| `user_roles.role_id` | `roles.id` | Association user–role | Đăng nhập/RBAC, mọi NV |
| `role_permissions.role_id` | `roles.id` | Association role–permission | Đăng nhập/RBAC, mọi NV |
| `role_permissions.permission_id` | `permissions.id` | Association role–permission | Đăng nhập/RBAC, mọi NV |
| `work_shifts.staff_user_id` | `users.id` | Nhân viên phụ trách ca | NV03–05, NV08 |
| `work_shifts.confirmed_by_user_id` | `users.id` | User xác nhận ca | NV08 |
| `audit_logs.actor_user_id` | `users.id` | Actor của audit record | NV01–08 |

## Apartment / Resident / Vehicle

| Source table.field | Target | Semantic relationship | Workflow |
|---|---|---|---|
| `apartment_memberships.apartment_id` | `apartments.id` | Apartment endpoint của membership | NV01 |
| `apartment_memberships.resident_id` | `residents.id` | Resident endpoint của membership | NV01 |
| `vehicle_categories.family_id` | `vehicle_families.id` | Category thuộc vehicle family | NV01–02, NV05–06 |
| `vehicles.vehicle_category_id` | `vehicle_categories.id` | Category của xe đã đăng ký | NV01–06 |
| `vehicle_resident_relations.vehicle_id` | `vehicles.id` | Vehicle endpoint của owner/authorized relation | NV01, NV03–04 |
| `vehicle_resident_relations.resident_id` | `residents.id` | Resident endpoint của owner/authorized relation | NV01, NV03–04 |
| `parking_subscriptions.vehicle_id` | `vehicles.id` | Xe được đăng ký gói | NV02–04 |
| `parking_sessions.registered_vehicle_id` | `vehicles.id` | Xe đăng ký liên quan đến lượt | NV03–05 |
| `parking_sessions.entry_vehicle_category_id` | `vehicle_categories.id` | Category được ghi cho lượt vào | NV03–05 |
| `gate_events.driver_resident_id` | `residents.id` | Cư dân được resolve là người lái | NV03–04 |
| `gate_events.resolved_vehicle_id` | `vehicles.id` | Xe được resolve từ sự kiện | NV03–05 |
| `gate_events.resolved_vehicle_category_id` | `vehicle_categories.id` | Category được resolve trong sự kiện | NV03–06 |

Business meaning của apartment membership và vehicle authorization xem [NV01](../business/nv01-resident-vehicle-registration.md). Vehicle relation không thay thế membership.

### AHR-01 project-level persistence additions (not original ERD relationships)

| Source table.field | Target | Semantic relationship | Provenance |
|---|---|---|---|
| `vehicle_resident_relations.guarantor_resident_id` | `residents.id` | Business guarantor for an AUTHORIZED_USER relation; distinct from the system actor recorded by audit | Approved AHR-01 project decision, materialized in V4 |
| `vehicle_resident_relations.guarantor_apartment_id` | `apartments.id` | Apartment context for a HOUSEHOLD_HEAD guarantor chain | Approved AHR-01 project decision, materialized in V4 |

The V4 guarantor/context foreign keys use restrictive deletion behavior to preserve relation history. Canonical identity-key fields are not foreign keys. These additions are not original `ERD.pdf` connectors.

### AHR review-remediation persistence additions (V5; not original ERD relationships)

| Source table.field | Target | Semantic relationship | Provenance |
|---|---|---|---|
| `vehicle_right_pending_transitions.vehicle_right_id` | `vehicle_resident_relations.id` | One pending future terminal transition for a VehicleRight | Approved AHR review-remediation decision, materialized in V5; restrictive FK |
| `vehicle_right_pending_transitions.source_actor_user_id` | `users.id` | Actor whose source command scheduled the transition | Approved AHR review-remediation decision, materialized in V5; restrictive FK |

These pending-transition links are project-level remediation, not original ERD relationships.

## Card

| Source table.field | Target | Semantic relationship | Workflow |
|---|---|---|---|
| `cards.replaced_card_id` | `cards.id` | Tham chiếu thẻ được thay thế (self-reference) | NV02 |
| `card_assignments.card_id` | `cards.id` | Card endpoint của assignment | NV02 |
| `card_assignments.resident_id` | `residents.id` | Resident endpoint của card assignment | NV02 |
| `parking_sessions.access_card_id` | `cards.id` | Thẻ được ghi trên Parking Session | NV03–05 |
| `gate_events.card_id` | `cards.id` | Thẻ được ghi nhận trong Gate Event | NV03–05 |

`Card` khác `Card Assignment`; visitor card có `card_type` trong ERD nhưng quy tắc visitor card assignment không suy ra từ relationship này.

## Pricing / Subscription / Gate Policy

| Source table.field | Target | Semantic relationship | Workflow |
|---|---|---|---|
| `pricing_versions.created_by_user_id` | `users.id` | User tạo pricing version | NV02, NV05 |
| `monthly_rates.pricing_version_id` | `pricing_versions.id` | Monthly rate thuộc pricing version | NV02 |
| `monthly_rates.vehicle_category_id` | `vehicle_categories.id` | Monthly rate áp dụng theo category | NV02 |
| `visitor_period_rates.pricing_version_id` | `pricing_versions.id` | Visitor period rate thuộc pricing version | NV05 |
| `visitor_period_rates.vehicle_family_id` | `vehicle_families.id` | Visitor period rate theo vehicle family | NV05 |
| `visitor_car_rate_rules.pricing_version_id` | `pricing_versions.id` | Quy tắc ô tô thuộc pricing version | NV05 |
| `visitor_car_rate_rules.vehicle_family_id` | `vehicle_families.id` | Quy tắc ô tô theo vehicle family | NV05 |
| `parking_subscriptions.monthly_rate_id` | `monthly_rates.id` | Gói tham chiếu mức giá đã chọn | NV02 |
| `parking_subscriptions.created_by_user_id` | `users.id` | User tạo subscription | NV02 |
| `decision_policy_versions.created_by_user_id` | `users.id` | User tạo decision policy version | NV03–07 |
| `gate_events.decision_policy_version_id` | `decision_policy_versions.id` | Policy version gắn event decision | NV03–07 |

## Gate / Parking Session / Review / Barrier

| Source table.field | Target | Semantic relationship | Workflow |
|---|---|---|---|
| `gate_lanes.station_id` | `gate_stations.id` | Lane thuộc station | NV03–07 |
| `work_shifts.lane_id` | `gate_lanes.id` | Ca làm việc gắn lane | NV03–05, NV08 |
| `gate_events.parking_session_id` | `parking_sessions.id` | Event liên hệ Parking Session | NV03–05 |
| `gate_events.lane_id` | `gate_lanes.id` | Lane phát sinh event | NV03–07 |
| `gate_events.work_shift_id` | `work_shifts.id` | Shift liên quan event | NV03–05, NV08 |
| `gate_events.operator_user_id` | `users.id` | User vận hành event | NV03–07 |
| `manual_reviews.gate_event_id` | `gate_events.id` | Review của event | NV03–07 |
| `manual_reviews.reviewer_user_id` | `users.id` | User review | NV07 |
| `barrier_actions.gate_event_id` | `gate_events.id` | Barrier action liên kết event | NV03–07 |
| `barrier_actions.requested_by_user_id` | `users.id` | User yêu cầu barrier action | NV03–07 |
| `alerts.gate_event_id` | `gate_events.id` | Alert liên quan event | NV07–08 |
| `alerts.parking_session_id` | `parking_sessions.id` | Alert liên quan session | NV07–08 |
| `alerts.acknowledged_by_user_id` | `users.id` | User acknowledge alert | NV07–08 |
| `alerts.resolved_by_user_id` | `users.id` | User resolve alert | NV07–08 |

Parking Session không phải Gate Event. Review và Barrier Action là hai bảng riêng cùng tham chiếu Gate Event.

## Media / Identity Verification / AI

| Source table.field | Target | Semantic relationship | Workflow |
|---|---|---|---|
| `resident_media.resident_id` | `residents.id` | Resident-media association | NV01 |
| `resident_media.media_id` | `media_assets.id` | Media endpoint của resident media | NV01 |
| `gate_event_media.gate_event_id` | `gate_events.id` | Gate Event-media association | NV03–07 |
| `gate_event_media.media_id` | `media_assets.id` | Media endpoint của event media | NV03–07 |
| `face_templates.resident_id` | `residents.id` | Template thuộc resident | NV01, NV03–04 |
| `face_templates.source_media_id` | `media_assets.id` | Media nguồn template | NV01 |
| `identity_verifications.resident_id` | `residents.id` | Verification của resident | NV01 |
| `identity_verifications.cccd_media_id` | `media_assets.id` | CCCD face input | NV01 |
| `identity_verifications.registration_media_id` | `media_assets.id` | Registration face input | NV01 |
| `identity_verifications.realtime_media_id` | `media_assets.id` | Realtime face input | NV01 |
| `identity_verifications.verified_by_user_id` | `users.id` | User verifier | NV01 |
| `ai_inference_results.gate_event_id` | `gate_events.id` | Inference attempt của event | NV03–07 |
| `ai_inference_results.predicted_vehicle_family_id` | `vehicle_families.id` | Vehicle family model dự đoán | NV06 |
| `ai_inference_results.matched_face_template_id` | `face_templates.id` | Face template được inference match | NV01, NV03–04 |

## Billing / Shifts

| Source table.field | Target | Semantic relationship | Workflow |
|---|---|---|---|
| `charges.parking_subscription_id` | `parking_subscriptions.id` | Charge liên quan subscription | NV02 |
| `charges.parking_session_id` | `parking_sessions.id` | Charge liên quan session | NV05 |
| `charges.pricing_version_id` | `pricing_versions.id` | Charge lưu version giá áp dụng | NV02, NV05 |
| `charge_items.charge_id` | `charges.id` | Item thuộc Charge | NV02, NV05 |
| `payments.charge_id` | `charges.id` | Payment liên quan Charge | NV02, NV05 |
| `payments.original_payment_id` | `payments.id` | Giao dịch gốc được liên kết (self-reference) | NV02, NV05 |
| `payments.work_shift_id` | `work_shifts.id` | Payment liên quan shift | NV02, NV05, NV08 |
| `payments.processed_by_user_id` | `users.id` | User xử lý payment | NV02, NV05 |

Charge và Payment là concept riêng; payment records có các type/status riêng trong [enums](enums.md).

## Foreign Key / Cardinality Notes

Các bảng/field phía trên là các quan hệ FK được thể hiện bằng connector trong ERD. `origin_event_id` là ID nguồn dạng `char(36)` nhưng ERD không nối nó tới bảng nào; không ghi nhận là FK. ERD/source không xác định cascade behavior. Không suy ra 1:1 nếu không có unique marker rõ trong nguồn.
