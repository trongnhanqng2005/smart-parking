# ERD Enums

Status: Approved specification materialization  
Source: `ERD.pdf` — pages 3–6; `CNTT_KLCN101_Tran Van Tho.md`; `Ket_Qua_Khao_Sat_Bai_Xe.md`  
Implementation status: All 38 enum definitions are represented by Java enums and matching MySQL `ENUM` column literals in `V1__create_approved_schema.sql`; V1 is applied and Hibernate validation succeeds.

ERD định nghĩa **38 enum types** dưới đây. Tên enum và literal được giữ nguyên chính tả/viết hoa. Ý nghĩa state/transition chỉ nêu khi business source chỉ rõ; trường hợp khác ghi đúng **“Detailed transition/business meaning not specified by approved sources.”**

## `user_status`

Used by: `users.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `LOCKED` | Detailed transition/business meaning not specified by approved sources. |
| `DISABLED` | Detailed transition/business meaning not specified by approved sources. |

## `resident_status`

Used by: `residents.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `INACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `BLOCKED` | Detailed transition/business meaning not specified by approved sources. |

## `apartment_status`

Used by: `apartments.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `INACTIVE` | Detailed transition/business meaning not specified by approved sources. |

## `membership_role`

Used by: `apartment_memberships.member_role`

| Value | Meaning from source |
|---|---|
| `HOUSEHOLD_HEAD` | Vai trò chủ hộ được đề cương nêu trong NV01. |
| `MEMBER` | Thành viên hộ gia đình theo NV01. |

## `relation_status`

Used by: `apartment_memberships.status`, `vehicle_resident_relations.status`, `card_assignments.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `INACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `REVOKED` | Detailed transition/business meaning not specified by approved sources. |

## `vehicle_relation_type`

Used by: `vehicle_resident_relations.relation_type`

| Value | Meaning from source |
|---|---|
| `OWNER` | Chủ sở hữu xe trong quan hệ với cư dân. |
| `AUTHORIZED_USER` | Người được ủy quyền sử dụng xe; survey yêu cầu người dùng chỉ sử dụng xe đã được ủy quyền. |

## `vehicle_status`

Used by: `vehicles.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `INACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `BLOCKED` | Detailed transition/business meaning not specified by approved sources. |

## `card_type`

Used by: `cards.card_type`

| Value | Meaning from source |
|---|---|
| `RESIDENT` | Thẻ cư dân. |
| `VISITOR` | Thẻ khách vãng lai được đề cương nêu tại cổng. |

## `card_status`

Used by: `cards.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `LOCKED` | Thẻ bị khóa; đề cương nêu tạm khóa thẻ khi mất hoặc vi phạm. |
| `LOST` | Thẻ được ghi nhận mất theo quy trình quản lý thẻ. |
| `CANCELLED` | Detailed transition/business meaning not specified by approved sources. |
| `REPLACED` | Thẻ đã được thay thế theo NV02. |

## `pricing_status`

Used by: `pricing_versions.status`, `decision_policy_versions.status`

| Value | Meaning from source |
|---|---|
| `DRAFT` | Detailed transition/business meaning not specified by approved sources. |
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `INACTIVE` | Detailed transition/business meaning not specified by approved sources. |

## `pricing_period_type`

Used by: `visitor_period_rates.period_type`

| Value | Meaning from source |
|---|---|
| `DAY` | Period giá ban ngày; survey nêu khoảng 06:00–17:59 cho xe máy/xe đạp. |
| `NIGHT` | Period giá ban đêm; survey nêu khoảng 18:00–05:59 cho xe máy/xe đạp. |
| `OVERNIGHT_CYCLE` | Chu kỳ qua đêm; cách tính cụ thể theo loại xe ở survey §2. |

## `subscription_status`

Used by: `parking_subscriptions.status`

| Value | Meaning from source |
|---|---|
| `PENDING` | Detailed transition/business meaning not specified by approved sources. |
| `ACTIVE` | Gói được kích hoạt sau khi thanh toán thành công theo survey §4.1. |
| `EXPIRED` | Gói 30 ngày hết hạn; không tự đổi cư dân thành khách theo survey §5. |
| `SUSPENDED` | Detailed transition/business meaning not specified by approved sources. |
| `CANCELLED` | Detailed transition/business meaning not specified by approved sources. |

## `station_status`

Used by: `gate_stations.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `INACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `MAINTENANCE` | Detailed transition/business meaning not specified by approved sources. |

## `lane_status`

Used by: `gate_lanes.status`

| Value | Meaning from source |
|---|---|
| `ACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `INACTIVE` | Detailed transition/business meaning not specified by approved sources. |
| `MAINTENANCE` | Detailed transition/business meaning not specified by approved sources. |

## `lane_direction`

Used by: `gate_lanes.direction`

| Value | Meaning from source |
|---|---|
| `ENTRY` | Làn hướng vào. |
| `EXIT` | Làn hướng ra. |
| `BOTH` | Làn hai hướng theo literal ERD. |

## `shift_status`

Used by: `work_shifts.status`

| Value | Meaning from source |
|---|---|
| `OPEN` | Ca đang mở theo literal ERD. |
| `CLOSED` | Ca đã đóng; survey yêu cầu đối soát cuối ca. |

## `parking_customer_type`

Used by: `parking_sessions.customer_type`

| Value | Meaning from source |
|---|---|
| `RESIDENT` | Lượt xe cư dân. |
| `VISITOR` | Lượt khách vãng lai. |

## `parking_session_status`

Used by: `parking_sessions.status`

| Value | Meaning from source |
|---|---|
| `OPEN` | Lượt xe đang trong bãi/chưa đóng; cổng ra tìm lượt OPEN. |
| `CLOSED` | Lượt đã đóng sau xử lý ra. |
| `CLOSED_MANUAL` | Nhân viên xác nhận xe đã ra và đóng lượt thủ công sau tình huống barrier mở nhưng lượt chưa đóng. |
| `CANCELLED` | Detailed transition/business meaning not specified by approved sources. |
| `SYNC_CONFLICT` | Lượt có xung đột đồng bộ; survey yêu cầu giữ conflict để quản lý xử lý, không tự ghi đè. |

## `gate_event_type`

Used by: `gate_events.event_type`

| Value | Meaning from source |
|---|---|
| `ENTRY` | Sự kiện vào cổng. |
| `EXIT` | Sự kiện ra cổng. |

## `decision_type`

Used by: `gate_events.decision`

| Value | Meaning from source |
|---|---|
| `AUTO_APPROVE` | Quyết định tự động cho phép theo kiểm tra nghiệp vụ. |
| `MANUAL_REVIEW` | Cần nhân viên/manager review theo ngoại lệ. |
| `REJECT` | Từ chối tự động trong flow có điều kiện không đạt. |

## `decision_outcome`

Used by: `gate_events.final_outcome`

| Value | Meaning from source |
|---|---|
| `PENDING` | Kết quả đang chờ xử lý theo literal ERD. |
| `ALLOWED` | Được phép theo kết quả nghiệp vụ. |
| `REJECTED` | Bị từ chối theo kết quả nghiệp vụ. |

## `operating_mode`

Used by: `gate_events.operating_mode`

| Value | Meaning from source |
|---|---|
| `ONLINE` | Xử lý khi kết nối hoạt động. |
| `OFFLINE` | Máy trạm hoạt động trong chế độ dự phòng khi mất kết nối. |

## `sync_status`

Used by: `gate_events.sync_status`

| Value | Meaning from source |
|---|---|
| `NOT_REQUIRED` | Đồng bộ không cần thiết theo literal ERD. |
| `PENDING` | Chờ đồng bộ. |
| `SYNCED` | Đã đồng bộ; server trả mã lượt chính thức theo survey. |
| `CONFLICT` | Có xung đột; không tự ghi đè theo survey. |

## `resolution_source`

Used by: `gate_events.plate_resolution_source`, `gate_events.vehicle_resolution_source`, `gate_events.driver_resolution_source`

| Value | Meaning from source |
|---|---|
| `AI` | Giá trị resolve có nguồn từ inference AI. |
| `MANUAL` | Giá trị được nhân viên chọn/sửa; ví dụ loại xe AI không xác định. |
| `REGISTERED_DATA` | Giá trị resolve từ hồ sơ đã đăng ký. |

## `manual_review_decision`

Used by: `manual_reviews.decision`

| Value | Meaning from source |
|---|---|
| `APPROVE` | Người review phê duyệt xử lý. |
| `REJECT` | Người review từ chối xử lý. |

## `barrier_action_type`

Used by: `barrier_actions.action`

| Value | Meaning from source |
|---|---|
| `OPEN` | Yêu cầu mở barrier. |
| `CLOSE` | Yêu cầu đóng barrier. |

## `barrier_trigger_source`

Used by: `barrier_actions.trigger_source`

| Value | Meaning from source |
|---|---|
| `AUTO` | Action được kích hoạt tự động theo literal ERD. |
| `MANUAL` | Action do thao tác thủ công. |

## `barrier_action_result`

Used by: `barrier_actions.result`

| Value | Meaning from source |
|---|---|
| `REQUESTED` | Action đã được yêu cầu. |
| `SUCCESS` | Action thành công. |
| `FAILED` | Action thất bại. |

## `ai_inference_status`

Used by: `ai_inference_results.status`

| Value | Meaning from source |
|---|---|
| `SUCCESS` | Detailed transition/business meaning not specified by approved sources. |
| `PARTIAL` | Detailed transition/business meaning not specified by approved sources. |
| `FAILED` | AI inference thất bại; nghiệp vụ chuyển xử lý sự cố/manual khi cần. |

## `verification_result`

Used by: `identity_verifications.result`

| Value | Meaning from source |
|---|---|
| `PASS` | Detailed transition/business meaning not specified by approved sources. |
| `REVIEW` | Cần xử lý/review theo flow xác minh. |
| `FAIL` | Detailed transition/business meaning not specified by approved sources. |

## `resident_media_purpose`

Used by: `resident_media.purpose`

| Value | Meaning from source |
|---|---|
| `CCCD_FACE` | Ảnh khuôn mặt từ CCCD. |
| `REGISTRATION_FACE` | Ảnh khuôn mặt đăng ký. |
| `OTHER` | Detailed transition/business meaning not specified by approved sources. |

## `gate_media_purpose`

Used by: `gate_event_media.purpose`

| Value | Meaning from source |
|---|---|
| `PLATE_IMAGE` | Ảnh biển số. |
| `DRIVER_FACE` | Ảnh mặt người lái. |
| `VEHICLE_IMAGE` | Ảnh phương tiện. |
| `OVERVIEW_IMAGE` | Ảnh tổng quan. |

## `charge_status`

Used by: `charges.status`

| Value | Meaning from source |
|---|---|
| `UNPAID` | Charge mới tạo, chưa thanh toán theo survey §4.1. |
| `PAYMENT_PENDING` | Thanh toán chờ xử lý/lỗi hoặc chưa đủ tiền; không được ghi `PAID` giả. |
| `PAID` | Payment thành công. |
| `REFUNDED` | Charge đã hoàn theo flow được quản lý cho phép. |
| `VOID` | Charge bị hủy theo flow có quyền và lý do. |
| `ADJUSTED` | Khoản được điều chỉnh bởi người có quyền, có lý do. |

## `payment_type`

Used by: `payments.payment_type`

| Value | Meaning from source |
|---|---|
| `PAYMENT` | Giao dịch thanh toán. |
| `REFUND` | Giao dịch hoàn tiền. |
| `ADJUSTMENT` | Giao dịch điều chỉnh. |

## `payment_method`

Used by: `payments.payment_method`

| Value | Meaning from source |
|---|---|
| `CASH` | Tiền mặt; gắn ca nhân viên. |
| `BANK_TRANSFER` | Chuyển khoản; giao dịch điện tử có mã đối soát. |
| `QR` | QR; giao dịch điện tử có mã đối soát. |
| `OTHER` | Phương thức khác theo literal ERD. |

## `payment_status`

Used by: `payments.status`

| Value | Meaning from source |
|---|---|
| `PENDING` | Chờ kết quả payment. |
| `SUCCESS` | Payment thành công; mới chuyển Charge `PAID`. |
| `FAILED` | Payment thất bại; Charge không được giả `PAID`. |
| `VOIDED` | Payment bị void theo flow được quyền xử lý. |

## `alert_severity`

Used by: `alerts.severity`

| Value | Meaning from source |
|---|---|
| `INFO` | Detailed transition/business meaning not specified by approved sources. |
| `WARNING` | Detailed transition/business meaning not specified by approved sources. |
| `CRITICAL` | Detailed transition/business meaning not specified by approved sources. |

## `alert_status`

Used by: `alerts.status`

| Value | Meaning from source |
|---|---|
| `OPEN` | Cảnh báo đang mở/chưa giải quyết theo literal. |
| `ACKNOWLEDGED` | Alert đã được người dùng xác nhận nhận. |
| `RESOLVED` | Alert đã được đánh dấu giải quyết. |
