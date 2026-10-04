# Data Dictionary

Status: Approved specification materialization  
Source: `ERD.pdf` — pages 1–2; `CNTT_KLCN101_Tran Van Tho.md` — NV01–NV08; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §1–5  
Implementation status: The original 38 ERD table definitions are represented by feature-owned JPA entities and V1; V2 applies the resident email width correction. V4 adds the approved AHR-01 identity/lifecycle fields. V5 applies the approved AHR review-remediation guarantor constraint correction and pending VehicleRight transition persistence; both are applied to local MySQL `smart_parking` with Hibernate validation passing.

## Reading Notes

- ERD hiển thị 38 tables. Các cột và SQL type bên dưới được chép theo ERD.
- `id bigint` là cột định danh được ERD hiển thị cho bảng có `id`; bảng nối giữ đúng hình thức ERD. FK được liệt kê trong [relationships](relationships.md).
- Meaning là mô tả tên trường ở mức tài liệu. Nullability, default, UNIQUE, index, cascade và ý nghĩa chi tiết không nêu trong nguồn được ghi **Not specified by approved ERD**, ngoại trừ các auth/RBAC thay đổi được phê duyệt và materialize trong V3.
- Các trường chứa ảnh, định danh, face template, audit hoặc dữ liệu thanh toán cần được đọc cùng chính sách bảo vệ dữ liệu tại business docs; tài liệu này không ấn định retention.

## `users`

**Purpose:** Tài khoản người dùng nội bộ. **Owner module:** `security`. **Primary key:** `id`. **NV:** nền tảng, NV07–08. V3 canonicalizes `username` by trim/lowercase, makes it non-null and unique, adds `credential_changed_at DATETIME(6)`, and changes the surrogate ID to `AUTO_INCREMENT`.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh user; V3 uses `AUTO_INCREMENT`. |
| username | varchar(100) | Tên đăng nhập ở dạng trim/lowercase canonical; unique, non-null from V3. |
| password_hash | varchar(255) | Password hash; nguồn yêu cầu băm mật khẩu. |
| full_name | varchar(150) | Họ tên hiển thị. |
| email | varchar(150) | Email. |
| phone | varchar(30) | Số điện thoại. |
| status | user_status | Trạng thái tài khoản. |
| last_login_at | datetime | Thời điểm đăng nhập gần nhất. |
| credential_changed_at | datetime(6) | Thời điểm đổi thông tin xác thực; V3 field used to invalidate previous JWTs and Web sessions. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |

## `roles`

**Purpose:** Vai trò RBAC. **Owner module:** `security`. **Primary key:** `id`. **NV:** nền tảng. V3 makes `code` non-null/unique and `id` auto-generated; supported business role codes are `MANAGEMENT` and `GATE_STAFF`.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh role; V3 uses `AUTO_INCREMENT`. |
| code | varchar(50) | Mã role; non-null and unique from V3. |
| name | varchar(100) | Tên role. |
| description | varchar(255) | Mô tả role. |

## `permissions`

**Purpose:** Quyền tác vụ/resource trong RBAC. **Owner module:** `security`. **Primary key:** `id`. **NV:** nền tảng, NV01–08. V3 makes `code` non-null/unique and `id` auto-generated.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh permission; V3 uses `AUTO_INCREMENT`. |
| code | varchar(100) | Mã permission; non-null and unique from V3. |
| name | varchar(150) | Tên permission. |
| resource | varchar(100) | Resource permission áp dụng. |
| action | varchar(50) | Action được mô tả. |
| description | varchar(255) | Mô tả permission. |

## `user_roles`

**Purpose:** Liên kết user với role. **Owner module:** `security`. **Primary key:** Thành phần khóa theo ký hiệu ERD: `user_id`, `role_id`. **NV:** nền tảng. V3 adds unique `user_id` so each user can have at most one business role; the composite primary key remains unchanged.

| Column | ERD Type | Meaning |
|---|---|---|
| user_id | bigint | User được gán role. |
| role_id | bigint | Role được gán. |
| assigned_at | datetime | Thời điểm gán. |

## `role_permissions`

**Purpose:** Liên kết role với permission. **Owner module:** `security`. **Primary key:** Thành phần khóa theo ký hiệu ERD: `role_id`, `permission_id`. **NV:** nền tảng.

| Column | ERD Type | Meaning |
|---|---|---|
| role_id | bigint | Role nhận permission. |
| permission_id | bigint | Permission được gán. |
| assigned_at | datetime | Thời điểm gán. |

## `apartments`

**Purpose:** Hồ sơ căn hộ. **Owner module:** `resident`. **Primary key:** `id`. **NV:** NV01.

The original ERD identity fields remain. V4/AHR-01 adds canonical binary identity keys as an approved project-level decision; these keys are persistence-only and not user-facing fields.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh căn hộ. |
| apartment_code | varchar(50) | Mã căn hộ. |
| building | varchar(100) | Tòa nhà. |
| floor_no | int | Tầng. |
| status | apartment_status | Trạng thái căn hộ. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |
| building_key | varbinary(2048) | V4/AHR-01 key: NFC → trim → collapse internal whitespace → Unicode uppercase with `Locale.ROOT` → NFC → UTF-8 bytes. First part of the full-value unique Apartment identity index; MySQL compares binary bytes only. Not an original ERD field. |
| apartment_code_key | varbinary(768) | V4/AHR-01 key: NFC → trim → Unicode uppercase with `Locale.ROOT` → NFC → UTF-8 bytes. Preserve punctuation/internal spacing; second part of the full-value unique Apartment index. MySQL compares bytes only. Not an original ERD field. |

## `residents`

**Purpose:** Hồ sơ cá nhân cư dân. **Owner module:** `resident`. **Primary key:** `id`. **NV:** NV01, NV03–04.

V4/AHR-01 adds a canonical binary key for normalized identity-number uniqueness. It is sensitive persistence data, not an API profile field, and is not an original ERD field.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh cư dân. |
| full_name | varchar(150) | Họ tên. |
| identity_number | varchar(30) | Số định danh; dữ liệu nhạy cảm. |
| date_of_birth | date | Ngày sinh. |
| phone | varchar(30) | Số điện thoại. |
| email | varchar(150) | Email. |
| status | resident_status | Trạng thái cư dân. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |
| identity_number_key | varbinary(512) | V4/AHR-01 key: NFC → trim → remove internal whitespace → uppercase Latin letters only → NFC → UTF-8 bytes; unique and binary-compared. No NFKC/compatibility folding. Not an original ERD field. |

## `apartment_memberships`

**Purpose:** Quan hệ thành viên/chủ hộ giữa apartment và resident. **Owner module:** `resident`. **Primary key:** `id`. **NV:** NV01.

V4/AHR-01 adds lifecycle event metadata and a table-specific `VOID` status as approved project-level decisions. AHR-11 uses those existing columns/literals for created-in-error corrections: rows remain stored and their effective interval is preserved. The original shared `relation_status` mapping remains unchanged for `card_assignments`.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh quan hệ. |
| apartment_id | bigint | Căn hộ liên quan. |
| resident_id | bigint | Cư dân liên quan. |
| member_role | membership_role | Vai trò trong hộ. |
| valid_from | datetime | Bắt đầu hiệu lực. |
| valid_to | datetime | Kết thúc hiệu lực. |
| status | membership_status | V4 table-specific MySQL ENUM: `ACTIVE`, `INACTIVE`, `REVOKED`, `VOID`; `VOID` is an AHR-01 addition, not an original ERD enum literal. |
| created_at | datetime | Thời điểm tạo. |
| lifecycle_changed_at | datetime(6) | V4/AHR-01 lifecycle command/event time; distinct from the effective interval end in `valid_to`. AHR-11 records VOID command time here without changing the interval. |
| lifecycle_reason | varchar(500) | V4/AHR-01 business reason for lifecycle changes that require a reason; AHR-11 records the created-in-error reason for VOID. |

## `vehicle_families`

**Purpose:** Nhóm phương tiện cấp family. **Owner module:** `vehicle`. **Primary key:** `id`. **NV:** NV01, NV05–06.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh family. |
| code | varchar(50) | Mã family. |
| name | varchar(100) | Tên family. |

## `vehicle_categories`

**Purpose:** Nhóm/category phương tiện thuộc family. **Owner module:** `vehicle`. **Primary key:** `id`. **NV:** NV01–02, NV05–06.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh category. |
| family_id | bigint | Family chứa category. |
| code | varchar(50) | Mã category. |
| name | varchar(120) | Tên category. |
| description | varchar(255) | Mô tả category. |

## `vehicles`

**Purpose:** Hồ sơ xe. **Owner module:** `vehicle`. **Primary key:** `id`. **NV:** NV01–06.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh xe. |
| vehicle_category_id | bigint | Category xe. |
| plate_number | varchar(30) | Biển số. |
| plate_normalized | varchar(30) | Biển số chuẩn hóa dùng tra cứu. |
| brand | varchar(100) | Hãng xe. |
| model | varchar(100) | Model xe. |
| color | varchar(50) | Màu xe. |
| seat_count | int | Số chỗ. |
| engine_capacity_cc | int | Dung tích động cơ cc. |
| is_electric | boolean | Cờ xe điện. |
| status | vehicle_status | Trạng thái xe. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |

## `vehicle_resident_relations`

**Purpose:** Quan hệ chủ xe/người được phép dùng xe. **Owner module:** `vehicle`. **Primary key:** `id`. **NV:** NV01, NV03–04.

V4/AHR-01 adds business-guarantor/context links and table-specific lifecycle state/metadata. V5 applies the approved AHR review-remediation CHECK correction requiring `guarantor_type` for AUTHORIZED_USER. These are approved project-level additions after the original ERD; AHR-11 uses the existing `VOID` state/metadata to preserve created-in-error history without changing the effective interval, and `card_assignments.status` is unchanged.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh quan hệ. |
| vehicle_id | bigint | Xe được liên kết. |
| resident_id | bigint | Cư dân liên kết. |
| relation_type | vehicle_relation_type | `OWNER` hoặc `AUTHORIZED_USER`. |
| valid_from | datetime | Bắt đầu hiệu lực. |
| valid_to | datetime | Kết thúc hiệu lực. |
| status | vehicle_relation_status | V4 table-specific MySQL ENUM: `ACTIVE`, `INACTIVE`, `REVOKED`, `VOID`, `PRE_EFFECTIVE_CANCELLED`; the last two are AHR-01 additions, not original ERD literals. |
| created_at | datetime | Thời điểm tạo. |
| guarantor_type | enum('OWNER','HOUSEHOLD_HEAD') | V4/AHR-01 basis of business guarantor for AUTHORIZED_USER; null for OWNER relations. |
| guarantor_resident_id | bigint | V4/AHR-01 business guarantor Resident FK; required for AUTHORIZED_USER. |
| guarantor_apartment_id | bigint | V4/AHR-01 Apartment-context FK; required iff the guarantor is HOUSEHOLD_HEAD. |
| lifecycle_changed_at | datetime(6) | V4/AHR-01 lifecycle event time; for PRE_EFFECTIVE_CANCELLED it records the cancellation time, with no separate `cancelled_at` field. AHR-11 records the VOID command time without changing the effective interval. |
| lifecycle_reason | varchar(500) | V4/AHR-01 business reason for lifecycle changes that require a reason; AHR-11 stores the required created-in-error explanation. |

## `vehicle_right_pending_transitions`

**Purpose:** Durable storage for a future-dated dependent VehicleRight terminal transition. **Owner module:** `vehicle`. **Primary key:** `id`. This table and its constraints are an approved AHR review-remediation persistence decision, not an original ERD table. V5 creates the storage; the due-time transition processor is a later AHRR task.

| Column | Type | Meaning |
|---|---|---|
| id | bigint | Generated pending-transition row identifier. |
| vehicle_right_id | bigint | Unique VehicleRight target; restrictive FK preserves the target relation. At most one pending transition is stored per VehicleRight. |
| effective_at | datetime | Approved future transition instant, using the same precision as VehicleRight effective intervals. |
| reason | varchar(500) | Nonblank source-command reason retained for the deferred lifecycle audit. |
| source_actor_user_id | bigint | Required originating management actor; restrictive FK preserves attribution. |

## `cards`

**Purpose:** Thẻ cư dân hoặc khách. **Owner module:** `card`. **Primary key:** `id`. **NV:** NV02–05, NV07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh thẻ. |
| card_uid | varchar(100) | UID thẻ. |
| card_type | card_type | Loại thẻ. |
| status | card_status | Trạng thái thẻ. |
| issued_at | datetime | Thời điểm cấp. |
| expired_at | datetime | Thời điểm hết hạn. |
| replaced_card_id | bigint | Tham chiếu thẻ được thay thế theo ERD. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |

## `card_assignments`

**Purpose:** Lịch sử gán thẻ cư dân. **Owner module:** `card`. **Primary key:** `id`. **NV:** NV02.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh assignment. |
| card_id | bigint | Thẻ được gán. |
| resident_id | bigint | Cư dân nhận gán. |
| assigned_at | datetime | Thời điểm gán. |
| ended_at | datetime | Thời điểm kết thúc gán. |
| status | relation_status | Trạng thái quan hệ gán. |
| created_at | datetime | Thời điểm tạo. |

## `pricing_versions`

**Purpose:** Phiên bản bảng giá có thời gian hiệu lực. **Owner module:** `pricing`. **Primary key:** `id`. **NV:** NV02, NV05, NV08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh version. |
| version_code | varchar(50) | Mã version. |
| name | varchar(150) | Tên version. |
| description | varchar(500) | Mô tả. |
| effective_from | datetime | Bắt đầu hiệu lực. |
| effective_to | datetime | Kết thúc hiệu lực. |
| status | pricing_status | Trạng thái version. |
| created_by_user_id | bigint | User tạo version. |
| created_at | datetime | Thời điểm tạo. |

## `monthly_rates`

**Purpose:** Mức phí gói tháng/30 ngày theo category. **Owner module:** `pricing`. **Primary key:** `id`. **NV:** NV02.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh rate. |
| pricing_version_id | bigint | Version giá. |
| vehicle_category_id | bigint | Category xe áp dụng. |
| duration_days | int | Số ngày của mức giá. |
| amount | decimal(14,2) | Số tiền. |

## `visitor_period_rates`

**Purpose:** Mức giá khách theo family/period và thời gian. **Owner module:** `pricing`. **Primary key:** `id`. **NV:** NV05.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh rate. |
| pricing_version_id | bigint | Version giá. |
| vehicle_family_id | bigint | Family xe. |
| period_type | pricing_period_type | Loại period. |
| start_time | time | Bắt đầu khung giờ. |
| end_time | time | Kết thúc khung giờ. |
| cycle_minutes | int | Độ dài cycle bằng phút. |
| amount | decimal(14,2) | Số tiền áp dụng. |

## `visitor_car_rate_rules`

**Purpose:** Quy tắc tính giá khách ô tô. **Owner module:** `pricing`. **Primary key:** `id`. **NV:** NV05.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh rule. |
| pricing_version_id | bigint | Version giá. |
| vehicle_family_id | bigint | Family xe. |
| base_minutes | int | Thời lượng cơ sở. |
| base_fee | decimal(14,2) | Phí cơ sở. |
| increment_minutes | int | Độ dài mỗi increment. |
| increment_fee | decimal(14,2) | Phí mỗi increment. |
| overnight_min_fee | decimal(14,2) | Mức tối thiểu qua đêm theo cấu hình. |

## `decision_policy_versions`

**Purpose:** Phiên bản chính sách chứa ngưỡng quyết định. **Owner module:** `gate`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh policy version. |
| policy_code | varchar(50) | Mã policy. |
| name | varchar(150) | Tên policy. |
| plate_min_confidence | decimal(5,4) | Ngưỡng confidence biển số. |
| vehicle_min_confidence | decimal(5,4) | Ngưỡng confidence phương tiện. |
| face_min_similarity | decimal(5,4) | Ngưỡng similarity khuôn mặt. |
| liveness_min_score | decimal(5,4) | Ngưỡng liveness. |
| image_quality_min_score | decimal(5,4) | Ngưỡng chất lượng ảnh. |
| effective_from | datetime | Bắt đầu hiệu lực. |
| effective_to | datetime | Kết thúc hiệu lực. |
| status | pricing_status | Enum được ERD gán cho trường status. |
| created_by_user_id | bigint | User tạo policy. |
| created_at | datetime | Thời điểm tạo. |

## `parking_subscriptions`

**Purpose:** Gói gửi xe gắn với xe và mức giá. **Owner module:** `subscription`. **Primary key:** `id`. **NV:** NV02–04, NV08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh subscription. |
| vehicle_id | bigint | Xe sử dụng gói. |
| monthly_rate_id | bigint | Mức giá được chọn. |
| valid_from | datetime | Bắt đầu hiệu lực. |
| valid_until | datetime | Kết thúc hiệu lực. |
| status | subscription_status | Trạng thái gói. |
| created_by_user_id | bigint | User tạo gói. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |

## `gate_stations`

**Purpose:** Trạm cổng. **Owner module:** `gate`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh station. |
| station_code | varchar(50) | Mã station. |
| name | varchar(100) | Tên station. |
| hostname | varchar(150) | Hostname. |
| status | station_status | Trạng thái station. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |

## `gate_lanes`

**Purpose:** Làn thuộc trạm. **Owner module:** `gate`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh lane. |
| station_id | bigint | Station chứa lane. |
| lane_code | varchar(50) | Mã lane. |
| name | varchar(100) | Tên lane. |
| direction | lane_direction | Hướng hoạt động. |
| status | lane_status | Trạng thái lane. |

## `work_shifts`

**Purpose:** Ca làm việc gắn làn/nhân viên và đối soát cash. **Owner module:** `gate`. **Primary key:** `id`. **NV:** NV03–05, NV08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh shift. |
| shift_code | varchar(50) | Mã shift. |
| lane_id | bigint | Lane của ca. |
| staff_user_id | bigint | Nhân viên ca. |
| started_at | datetime | Bắt đầu ca. |
| ended_at | datetime | Kết thúc ca. |
| opening_cash | decimal(14,2) | Tiền đầu ca. |
| closing_cash | decimal(14,2) | Tiền cuối ca. |
| status | shift_status | Trạng thái ca. |
| confirmed_by_user_id | bigint | User xác nhận. |
| confirmed_at | datetime | Thời điểm xác nhận. |
| note | varchar(500) | Ghi chú. |

## `parking_sessions`

**Purpose:** Lượt đỗ xe từ thời điểm vào đến ra. **Owner module:** `parking`. **Primary key:** `id`. **NV:** NV03–05, NV07–08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh session. |
| customer_type | parking_customer_type | Cư dân hoặc khách. |
| registered_vehicle_id | bigint | Xe đăng ký nếu có. |
| access_card_id | bigint | Thẻ dùng cho lượt. |
| entry_plate | varchar(30) | Plate ghi nhận lúc vào. |
| entry_vehicle_category_id | bigint | Category lúc vào. |
| entered_at | datetime | Thời điểm vào. |
| exited_at | datetime | Thời điểm ra. |
| status | parking_session_status | Trạng thái session. |
| created_at | datetime | Thời điểm tạo. |
| updated_at | datetime | Thời điểm cập nhật. |

## `gate_events`

**Purpose:** Sự kiện xử lý tại gate, dữ liệu resolve và quyết định. **Owner module:** `gate`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh event. |
| origin_event_id | char(36) | ID event nguồn, dùng trong đồng bộ theo concept ERD. |
| parking_session_id | bigint | Session liên quan. |
| lane_id | bigint | Lane phát sinh event. |
| work_shift_id | bigint | Shift liên quan. |
| event_type | gate_event_type | ENTRY/EXIT theo enum. |
| occurred_at | datetime | Thời điểm xảy ra. |
| card_id | bigint | Card được ghi nhận. |
| driver_resident_id | bigint | Cư dân lái xe được resolve. |
| resolved_vehicle_id | bigint | Xe được resolve. |
| resolved_vehicle_category_id | bigint | Category được resolve. |
| resolved_plate | varchar(30) | Plate được resolve. |
| plate_resolution_source | resolution_source | Nguồn resolve plate. |
| vehicle_resolution_source | resolution_source | Nguồn resolve vehicle. |
| driver_resolution_source | resolution_source | Nguồn resolve driver. |
| decision_policy_version_id | bigint | Policy version dùng cho event. |
| decision | decision_type | Loại quyết định. |
| final_outcome | decision_outcome | Kết quả cuối. |
| decision_reason_code | varchar(100) | Mã lý do. |
| decision_note | varchar(500) | Ghi chú quyết định. |
| operating_mode | operating_mode | Online/offline. |
| sync_status | sync_status | Trạng thái đồng bộ. |
| sync_conflict_reason | varchar(500) | Lý do conflict. |
| operator_user_id | bigint | User vận hành. |
| created_at | datetime | Thời điểm tạo. |

## `manual_reviews`

**Purpose:** Kết quả Manual Review cho Gate Event. **Owner module:** `gate`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh review. |
| gate_event_id | bigint | Event được review. |
| reviewer_user_id | bigint | User review. |
| decision | manual_review_decision | APPROVE/REJECT. |
| reason_code | varchar(100) | Mã lý do. |
| note | varchar(1000) | Ghi chú. |
| reviewed_at | datetime | Thời điểm review. |
| created_at | datetime | Thời điểm tạo. |

## `barrier_actions`

**Purpose:** Ghi nhận action điều khiển barrier độc lập với review. **Owner module:** `gate`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh action. |
| gate_event_id | bigint | Event liên quan. |
| action | barrier_action_type | OPEN/CLOSE. |
| trigger_source | barrier_trigger_source | AUTO/MANUAL. |
| requested_by_user_id | bigint | User yêu cầu. |
| requested_at | datetime | Thời điểm yêu cầu. |
| result | barrier_action_result | Kết quả action. |
| completed_at | datetime | Thời điểm hoàn tất. |
| failure_reason | varchar(500) | Lý do thất bại. |

## `media_assets`

**Purpose:** Metadata/reference của asset media. **Owner module:** `media`. **Primary key:** `id`. **NV:** NV01, NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh media. |
| storage_path | varchar(500) | Đường dẫn lưu trữ. |
| mime_type | varchar(100) | MIME type. |
| file_size_bytes | bigint | Kích thước byte. |
| sha256 | varchar(64) | SHA-256 checksum. |
| captured_at | datetime | Thời điểm capture. |
| created_at | datetime | Thời điểm tạo record. |

## `resident_media`

**Purpose:** Liên kết cư dân với media theo purpose. **Owner module:** `media`. **Primary key:** `id`. **NV:** NV01.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh liên kết. |
| resident_id | bigint | Cư dân liên quan. |
| media_id | bigint | Media liên quan. |
| purpose | resident_media_purpose | Mục đích media cư dân. |
| created_at | datetime | Thời điểm tạo. |

## `gate_event_media`

**Purpose:** Liên kết Gate Event với ảnh/evidence. **Owner module:** `media`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh liên kết. |
| gate_event_id | bigint | Gate Event liên quan. |
| media_id | bigint | Media liên quan. |
| purpose | gate_media_purpose | Loại evidence. |
| created_at | datetime | Thời điểm tạo. |

## `face_templates`

**Purpose:** Face template theo cư dân/model. **Owner module:** `media`. **Primary key:** `id`. **NV:** NV01, NV03–04.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh template. |
| resident_id | bigint | Cư dân template thuộc về. |
| source_media_id | bigint | Media nguồn. |
| model_version | varchar(100) | Model version. |
| template_storage_ref | varchar(500) | Reference nơi lưu template. |
| created_at | datetime | Thời điểm tạo. |
| revoked_at | datetime | Thời điểm revoke nếu có. |

## `identity_verifications`

**Purpose:** Kết quả xác minh ba ảnh của cư dân. **Owner module:** `resident`. **Primary key:** `id`. **NV:** NV01.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh verification. |
| resident_id | bigint | Cư dân được xác minh. |
| cccd_media_id | bigint | CCCD face media. |
| registration_media_id | bigint | Registration face media. |
| realtime_media_id | bigint | Realtime face media. |
| cccd_registration_score | decimal(5,4) | Score cặp CCCD–đăng ký. |
| registration_realtime_score | decimal(5,4) | Score cặp đăng ký–realtime. |
| cccd_realtime_score | decimal(5,4) | Score cặp CCCD–realtime. |
| threshold_used | decimal(5,4) | Ngưỡng ghi nhận khi xác minh. |
| result | verification_result | Kết quả verification. |
| model_version | varchar(100) | Model version. |
| verified_by_user_id | bigint | User thực hiện/xác nhận. |
| verified_at | datetime | Thời điểm xác minh. |
| created_at | datetime | Thời điểm tạo. |

## `ai_inference_results`

**Purpose:** Các lần inference gắn Gate Event. **Owner module:** `ai`. **Primary key:** `id`. **NV:** NV03–07.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh inference record. |
| gate_event_id | bigint | Gate Event liên quan. |
| attempt_no | int | Số lần thử. |
| status | ai_inference_status | Trạng thái inference. |
| predicted_plate | varchar(30) | Plate dự đoán. |
| plate_confidence | decimal(5,4) | Confidence plate. |
| predicted_vehicle_family_id | bigint | Family xe dự đoán. |
| vehicle_confidence | decimal(5,4) | Confidence xe. |
| matched_face_template_id | bigint | Face template khớp. |
| face_similarity | decimal(5,4) | Similarity khuôn mặt. |
| liveness_score | decimal(5,4) | Liveness score. |
| image_quality_score | decimal(5,4) | Image quality score. |
| anpr_model_version | varchar(100) | ANPR model version. |
| ocr_model_version | varchar(100) | OCR model version. |
| vehicle_model_version | varchar(100) | Vehicle model version. |
| face_model_version | varchar(100) | Face model version. |
| liveness_model_version | varchar(100) | Liveness model version. |
| processing_ms | int | Thời gian xử lý millisecond. |
| created_at | datetime | Thời điểm tạo. |

## `charges`

**Purpose:** Khoản phải thu gắn subscription hoặc parking session. **Owner module:** `billing`. **Primary key:** `id`. **NV:** NV02, NV05, NV08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh Charge. |
| invoice_no | varchar(50) | Số hóa đơn. |
| parking_subscription_id | bigint | Subscription được tính phí. |
| parking_session_id | bigint | Parking Session được tính phí. |
| pricing_version_id | bigint | Phiên bản giá áp dụng. |
| total_amount | decimal(14,2) | Tổng tiền phải thu. |
| status | charge_status | Trạng thái Charge. |
| created_at | datetime | Thời điểm tạo. |
| settled_at | datetime | Thời điểm settle. |

## `charge_items`

**Purpose:** Thành phần/chi tiết phép tính của Charge. **Owner module:** `billing`. **Primary key:** `id`. **NV:** NV02, NV05, NV08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh item. |
| charge_id | bigint | Charge cha. |
| component_code | varchar(100) | Mã thành phần phí. |
| description | varchar(255) | Mô tả. |
| amount | decimal(14,2) | Số tiền thành phần. |
| calculation_detail | json | Chi tiết calculation. |
| display_order | int | Thứ tự hiển thị. |
| created_at | datetime | Thời điểm tạo. |

## `payments`

**Purpose:** Giao dịch thanh toán/hoàn/điều chỉnh cho Charge. **Owner module:** `billing`. **Primary key:** `id`. **NV:** NV02, NV05, NV08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh Payment. |
| charge_id | bigint | Charge liên quan. |
| payment_type | payment_type | PAYMENT/REFUND/ADJUSTMENT. |
| payment_method | payment_method | Phương thức thanh toán. |
| amount | decimal(14,2) | Số tiền giao dịch. |
| status | payment_status | Trạng thái giao dịch. |
| external_reference | varchar(150) | Mã tham chiếu bên ngoài/đối soát. |
| receipt_no | varchar(50) | Số biên nhận. |
| original_payment_id | bigint | Payment gốc liên quan. |
| work_shift_id | bigint | Shift liên quan. |
| processed_by_user_id | bigint | User xử lý. |
| paid_at | datetime | Thời điểm thanh toán. |
| note | varchar(500) | Ghi chú. |
| created_at | datetime | Thời điểm tạo. |

## `alerts`

**Purpose:** Cảnh báo liên quan Gate Event/Parking Session. **Owner module:** `alert`. **Primary key:** `id`. **NV:** NV07–08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh alert. |
| gate_event_id | bigint | Gate Event liên quan. |
| parking_session_id | bigint | Parking Session liên quan. |
| alert_code | varchar(100) | Mã alert. |
| severity | alert_severity | Mức độ. |
| status | alert_status | Trạng thái alert. |
| message | varchar(1000) | Nội dung. |
| created_at | datetime | Thời điểm tạo. |
| acknowledged_by_user_id | bigint | User xác nhận đã nhận. |
| acknowledged_at | datetime | Thời điểm acknowledge. |
| resolved_by_user_id | bigint | User giải quyết. |
| resolved_at | datetime | Thời điểm resolve. |

## `audit_logs`

**Purpose:** Nhật ký hành động actor trên entity. **Owner module:** `audit`. **Primary key:** `id`; V3 changes the surrogate ID to `AUTO_INCREMENT`. **NV:** NV01–08.

| Column | ERD Type | Meaning |
|---|---|---|
| id | bigint | Định danh log. |
| actor_user_id | bigint | User thực hiện. |
| action | varchar(100) | Hành động. |
| entity_type | varchar(100) | Loại entity. |
| entity_id | varchar(100) | ID entity dạng chuỗi. |
| old_data | json | Dữ liệu trước. |
| new_data | json | Dữ liệu sau. |
| request_id | varchar(100) | Request ID. |
| client_ip | varchar(50) | Client IP. |
| created_at | datetime | Thời điểm ghi log. |
