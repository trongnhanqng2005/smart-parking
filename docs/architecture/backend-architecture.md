# Backend Architecture

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md`, `Ket_Qua_Khao_Sat_Bai_Xe.md`, `ERD.pdf`, `backend/pom.xml`, `backend/src/`  
Implementation status: Database and backend authentication/RBAC foundations are implemented. Approved NV01–NV08 business workflows remain unimplemented and are built incrementally as those capabilities are developed.

## Architecture

- **Modular Monolith**, **Package by Feature**, một Spring Boot deployable cho nghiệp vụ backend.
- REST API và Thymeleaf Web dùng chung business services.
- JPA/Hibernate persistence trên MySQL.
- AI service là thành phần riêng; AI Inference trả nhận diện/độ tin cậy, backend chịu trách nhiệm quyết định nghiệp vụ và quyền cổng.
- Không tách các module nghiệp vụ thành microservices.

`backend/pom.xml` xác nhận Java 21, Spring Boot 4.1.1, JPA, Security, Thymeleaf, Web MVC, Validation và MySQL driver. `src/` có persistence entities theo module sở hữu trong ERD. The authentication/RBAC foundation includes repositories, services, controllers and security configuration, audit integration, and persistence support. Repositories, services, and controllers for NV01–NV08 business capabilities are implemented incrementally as those capabilities are built.

## Base Package and Module Tree

```text
vn.edu.huit.smartparking.backend
├── common
├── config
├── security
├── resident
├── vehicle
├── card
├── pricing
├── subscription
├── parking
├── gate
├── media
├── ai
├── billing
├── alert
├── audit
└── report
```

## Module Responsibilities and Ownership

| Module | Responsibility and ERD concepts owned | NV | Major dependencies | Must not own |
|---|---|---|---|---|
| `common` | Thành phần kỹ thuật dùng chung nếu thực sự cần; không sở hữu ERD business table. | Cross-cutting | Không phụ thuộc feature. | Quy tắc nghiệp vụ tổng quát thay cho module owner. |
| `config` | Cấu hình Spring, persistence, web và runtime. | Cross-cutting | Framework/config. | Nghiệp vụ hoặc bảng business. |
| `security` | User, Role, Permission; `users`, `roles`, `permissions`, `user_roles`, `role_permissions`. | NV01–08; đăng nhập/RBAC | Có thể dùng `audit` tại thao tác quản trị. | Resident hoặc actor hồ sơ bãi xe. |
| `resident` | Apartment, Resident, membership và xác minh hồ sơ ba ảnh; `apartments`, `residents`, `apartment_memberships`, `identity_verifications`. | NV01, NV03–04 | `media`, `ai`; cung cấp resident identity cho `vehicle`/`gate`. | Vehicle authorization; quyền đó thuộc `vehicle`. |
| `vehicle` | Vehicle family/category, Vehicle và quan hệ owner/authorized user; `vehicle_families`, `vehicle_categories`, `vehicles`, `vehicle_resident_relations`. | NV01–06 | Resident identity; cung cấp dữ liệu cho `pricing`, `subscription`, `gate`. | Apartment membership hoặc subscription. |
| `card` | Thẻ và quan hệ gán theo thời gian; `cards`, `card_assignments`. | NV02–05, NV07 | Resident cho thẻ cư dân; `gate` tra trạng thái. | Parking Session hoặc quyết định cổng. |
| `pricing` | Version bảng giá, mức tháng, giá theo khung và quy tắc ô tô; `pricing_versions`, `monthly_rates`, `visitor_period_rates`, `visitor_car_rate_rules`. | NV02, NV05, NV08 | Vehicle category/family; cung cấp quy tắc cho `billing`. | Gói gửi xe hoặc giao dịch tiền. |
| `subscription` | Gói 30 ngày theo xe; `parking_subscriptions`. | NV02–04, NV08 | `vehicle`, `pricing`, payment result từ `billing`. | Giá, Charge hoặc Payment records. |
| `parking` | Vòng đời Parking Session; `parking_sessions`. | NV03–05, NV07–08 | Được `gate` điều phối; thông tin giá/thu phí qua billing workflow. | Gate Event, quyết định hoặc Barrier Action. |
| `gate` | Station, lane, shift, Gate Event, policy version, Manual Review, Barrier Action; `gate_stations`, `gate_lanes`, `work_shifts`, `decision_policy_versions`, `gate_events`, `manual_reviews`, `barrier_actions`. | NV03–07 | Điều phối `vehicle`, `card`, `subscription`, `parking`, `ai`, `media`, `billing`, `audit`, `alert`. | AI inference records, session lifecycle, payment records. |
| `media` | Media asset và quan hệ ảnh/template; `media_assets`, `resident_media`, `gate_event_media`, `face_templates`. | NV01, NV03–07 | Resident/Gate/AI dùng qua hành vi media. | Kết luận face verification hoặc gate authorization. |
| `ai` | AI inference cổng; `ai_inference_results`. | NV03–07, hỗ trợ NV01 | Tích hợp FastAPI; lưu kết quả tham chiếu media/template. | Gate business decision; ghi nhận inference khác với cho phép. |
| `billing` | Charge, charge item và Payment; `charges`, `charge_items`, `payments`. | NV02, NV05, NV07–08 | Pricing; tham chiếu subscription/session; shift/user. | Subscription activation policy hoặc Parking Session. |
| `alert` | Cảnh báo có trạng thái; `alerts`. | NV07–08 | Gate/Parking và người nhận/xác nhận. | Audit Log hoặc Review decision. |
| `audit` | Nhật ký hành động; `audit_logs`. | NV01–08 | Được các module gọi khi có thao tác cần lưu vết. | Alert hoặc business state. |
| `report` | Tra cứu, dashboard, revenue/reconciliation reports; không có bảng riêng trong ERD. | NV08 | Read-oriented view trên module sở hữu dữ liệu và `security`. | Ghi nghiệp vụ hoặc sở hữu lại bảng nguồn. |

`decision_policy_versions` thuộc `gate`: policy thresholds trực tiếp điều khiển Gate Event decision. `identity_verifications` thuộc `resident`: đây là xác minh danh tính/hồ sơ đăng ký; `ai_inference_results` vẫn thuộc `ai` để giữ tách biệt kết quả suy luận cổng. `gate` điều phối Parking Session và AI; không nhận ủy quyền quyết định từ AI.

## Required Domain Boundaries

- **Apartment != Resident:** `apartments` và `residents` là bảng riêng, nối qua `apartment_memberships`.
- **Vehicle != Vehicle Authorization:** `vehicles` và `vehicle_resident_relations` tách hồ sơ xe khỏi quyền owner/authorized user.
- **Card != Card Assignment:** `cards` độc lập `card_assignments`.
- **Pricing != Subscription:** bảng cấu hình giá tách khỏi quyền gói `parking_subscriptions`.
- **Parking Session != Gate Event:** lượt đỗ là khoảng thời gian; sự kiện cổng ghi từng lần vào/ra/xử lý.
- **AI Inference != Gate Business Decision:** inference/confidence là dữ liệu; backend quyết định theo quyền và quy tắc.
- **Manual Review != Barrier Action:** review là quyết định của người; action là yêu cầu/kết quả điều khiển barrier.
- **Charge != Payment:** khoản phải thu và giao dịch thanh toán/hoàn/điều chỉnh tách biệt.
- **Audit Log != Alert:** audit lưu hành động; alert là vấn đề vận hành cần theo dõi.

## Dependency Guidance

Feature module sở hữu và cập nhật Entity/table của mình. Module khác gọi hành vi nghiệp vụ của owner, không trực tiếp điều khiển lifecycle của Entity đó. `gate` là orchestrator của workflow cổng; `report` đọc dữ liệu. Không tạo vòng phụ thuộc service. Transaction boundary được xác định theo use case; không giữ DB Transaction trong lúc gọi AI, camera, barrier hay phương thức thanh toán bên ngoài.

## Internal Feature Structure

Đây là guideline, không phải yêu cầu tạo tất cả thư mục:

```text
feature/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

Tạo layer khi feature có nhu cầu; không tạo empty layers, generic repository abstraction hoặc interface chỉ để đủ mẫu. API contracts được viết khi endpoint tương ứng được thiết kế.
