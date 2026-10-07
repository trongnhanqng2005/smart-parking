# NV01 - Đăng ký căn hộ, cư dân và phương tiện

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 1 / NV01; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §4.4, §5; `ERD.pdf` — bảng resident/apartment/vehicle/media/verification  
Implementation status: The Apartment create, list, detail, history and correction API subset is implemented under AHR-03. The Resident create/reuse, list, detail, history, exact identity lookup and correction API subset is implemented under AHR-04. The Household Membership add, list, detail, history, household-head assign, END and REVOKE subset is implemented under AHR-05. Household-head transfer and Apartment deactivate/reactivate are implemented under AHR-06. Existing Vehicle lookup and OWNER assignment/transfer are implemented under AHR-07. AUTHORIZED_USER grant and VehicleRight list/detail/history queries are implemented under AHR-08. VehicleRight END/REVOKE and guarantor-loss integration are implemented under AHR-09. Resident status lifecycle and its guarantor-loss integration are implemented under AHR-10. Membership and VehicleRight VOID commands are implemented under AHR-11. Vehicle creation, identity verification and the remaining NV01 workflows are not implemented.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — §5 nghiệp vụ đặc trưng; §6 Yêu cầu 1; thang điểm Chức năng 01.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §4.4 bảo vệ dữ liệu; §5 cư dân chính chủ, vợ dùng xe đứng tên chồng, người thân chưa đăng ký, tiêu chí nghiệm thu.
- `ERD.pdf` — `apartments`, `residents`, `apartment_memberships`, `vehicle_families`, `vehicle_categories`, `vehicles`, `vehicle_resident_relations`, `media_assets`, `resident_media`, `face_templates`, `identity_verifications`.

## Objective

Đăng ký căn hộ, chủ hộ, thành viên hộ gia đình và phương tiện; ghi nhận chủ xe/quyền sử dụng xe; xác thực danh tính bằng cách so khớp ba cặp ảnh: CCCD–ảnh đăng ký, ảnh đăng ký–realtime, CCCD–realtime.

## Actors

- Ban quản lý: thao tác hồ sơ theo vai trò được cấp.
- Chủ hộ và thành viên hộ gia đình: người được đăng ký trong hồ sơ.
- Nhân viên được phân quyền: có thể hỗ trợ quy trình theo phân quyền.
- Dịch vụ AI: hỗ trợ face matching; không quyết định tư cách cư dân hoặc quyền dùng xe.

## Preconditions

- Căn hộ/hồ sơ người/xe và loại phương tiện cần được ghi nhận theo yêu cầu NV01.
- Ảnh CCCD, ảnh khuôn mặt đăng ký và ảnh khuôn mặt realtime được thu nhận để thực hiện ba so khớp.
- Các ràng buộc như số căn hộ, điều kiện cư trú hoặc người có thể sở hữu xe không được chi tiết hơn trong nguồn.

## Main Flow

1. Ghi thông tin căn hộ và chủ hộ.
2. Ghi các thành viên hộ gia đình và quan hệ thành viên/chủ hộ.
3. Ghi phương tiện, gồm biển số, chủ xe, loại phương tiện, hãng và thông số kỹ thuật.
4. Ghi quan hệ chủ xe hoặc người được phép sử dụng xe.
5. Thu nhận ảnh CCCD/ảnh đăng ký và ảnh khuôn mặt realtime.
6. So khớp ba cặp ảnh: CCCD–đăng ký, đăng ký–realtime, CCCD–realtime; lưu kết quả xác minh.

## Alternative / Exception Flows

- Khuôn mặt realtime không đạt chất lượng: không so sánh ngay; điều chỉnh camera/chụp lại; quá số lần thì nhân viên xử lý thủ công. Nguồn không ấn định số lần.
- Thành viên hộ chưa đăng ký hồ sơ mặt/ủy quyền: không tự động được cho phép sử dụng xe tại cổng.
- Vợ thuộc hộ, dùng xe đứng tên chồng và có ủy quyền: so sánh ảnh realtime với hồ sơ của chính người vợ, không dùng ảnh của người đứng tên xe; cho phép/review theo chính sách.
- Dữ liệu demo dùng người tự nguyện hoặc dữ liệu giả lập; không đưa ảnh CCCD/khuôn mặt thật lên AI service công cộng khi chưa có cơ sở và chấp thuận phù hợp.

## Business Rules

- **BR-NV01-001:** Đăng ký căn hộ, chủ hộ, thành viên hộ và quyền sử dụng xe là phạm vi NV01.
- **BR-NV01-002:** Hồ sơ xe có biển số, chủ xe, loại phương tiện, hãng và thông số kỹ thuật.
- **BR-NV01-003:** Xác minh đăng ký so sánh đúng ba cặp nguồn ảnh nêu tại Main Flow.
- **BR-NV01-004:** Thành viên hộ gia đình chỉ được xác minh bằng hồ sơ của chính họ và chỉ được dùng xe đã được ủy quyền.
- **BR-NV01-005:** Dùng dữ liệu demo tự nguyện hoặc giả lập; không gửi ảnh CCCD/khuôn mặt thật tới dịch vụ AI công cộng khi chưa có cơ sở và chấp thuận phù hợp.

## Data Involved

`apartments`, `residents`, `apartment_memberships`, `vehicle_families`, `vehicle_categories`, `vehicles`, `vehicle_resident_relations`, `media_assets`, `resident_media`, `face_templates`, `identity_verifications`. Xem [data dictionary](../database/data-dictionary.md) và [relationships](../database/relationships.md).

## State Changes

ERD định nghĩa trạng thái căn hộ, cư dân, quan hệ membership/vehicle relation và `verification_result`. Nguồn NV01 nêu kết quả xác minh và quyền có hiệu lực nhưng không quy định đầy đủ chuyển trạng thái hồ sơ: **Not specified by approved sources.** Quyết định dự án riêng tại AHR-10 phê duyệt năm hướng Resident status; AHR-11 materializes the separate created-in-error VOID lifecycle. Không xem những quyết định này là quy tắc từ nguồn NV01 gốc.

## AI Involvement

AI/face matching tạo scores/kết quả cho ba so khớp; NV01 cần ghi nhận kết quả xác minh, model version và ngưỡng được dùng theo ERD. AI không tự tạo quyền thành viên hoặc quan hệ ủy quyền xe.

## Security / Authorization

- RBAC phải phân biệt Ban quản lý và nhân viên trạm gác; nguyên tắc quyền tối thiểu.
- Nhân viên trạm gác chỉ được xem dữ liệu cần cho ca; Ban quản lý mới được sửa hồ sơ theo khảo sát.
- CCCD, ảnh mặt và face template là dữ liệu nhạy cảm: mã hóa khi lưu/truyền, kiểm soát vùng ảnh; ưu tiên template thay vì dùng ảnh thô cho mọi phép so sánh.

## Audit Requirements

Mọi lần xem/sửa/xóa hồ sơ phải có audit log. Thao tác thủ công liên quan cũng phải truy ra tài khoản, thời gian, lý do và ảnh bằng chứng khi áp dụng.

### Implemented AHR-03/AHR-04/AHR-05/AHR-06/AHR-07/AHR-08/AHR-09/AHR-10/AHR-11 audit behavior

- Apartment create/correction and successful detail reads are audited; Apartment list and history requests are not.
- Resident creation/reuse, correction and successful detail reads are audited. Every exact identity lookup request is audited once, including not-found outcomes. Resident list and history requests are not.
- Membership add, household-head assignment, END, REVOKE and successful detail reads are audited. Membership list and history requests are not; history returns only sanitized audit fields.
- AHR-06 household-head transfer audits the ended source Membership and the new Membership in one transaction. Apartment deactivate/reactivate and any nested Membership ENDs are audited in the same transaction as their state changes.
- AHR-07 Vehicle list lookup is not audited; successful Vehicle detail reads are audited. OWNER assignment and transfer-out/transfer-in events are audited in the same transaction as their relation changes.
- AHR-08 AUTHORIZED_USER grant and successful VehicleRight detail reads are audited. VehicleRight list and history reads are not audited. Grant audit records identify the management actor separately from the business guarantor and affected Resident relation.
- AHR-09 direct VehicleRight END/REVOKE and automatic guarantor-loss END/cancellation are audited in the same transaction as their source and relation changes. Automatic loss preserves the original guarantor fields, identifies the source command actor, and uses the source reason; it does not copy Resident profile data.
- AHR-10 Resident status transitions, explicit nested Membership/VehicleRight actions and automatic dependent-guarantor END/cancellation are audited in the same transaction. Audit data identifies relation IDs, target status, reason and actor without copying Resident profile data.
- AHR-11 Membership and VehicleRight VOID mutations, plus provably dependent AUTHORIZED_USER VOID cascades, are audited atomically with sanitized relation IDs, status, interval, lifecycle time, reason and actor. VOID does not physically delete rows or rewrite `valid_from`/`valid_to`; it is distinct from END, REVOKE and PRE_EFFECTIVE_CANCELLED.
- Audit data is sanitized: it does not include the full identity number, request/response snapshots or other unnecessary sensitive profile data. Resident summaries and history items do not embed the full Resident profile.
- This describes the implemented API subset; remaining NV01 operations retain the broader audit requirements above.

### Implemented AHR-09 VehicleRight lifecycle behavior

- Direct END and REVOKE apply to an existing VehicleRight and require `valid_from < effective_at <= command_time`; pre-start explicit REVOKE and future-effective direct commands are rejected. END sets `INACTIVE`; REVOKE sets `REVOKED`; both store `effective_at` in `valid_to` and retain command time/reason in lifecycle metadata.
- Loss of a guarantor chain through membership END/REVOKE, household-head transfer, Apartment deactivation with explicit membership ENDs, OWNER transfer, or direct OWNER END/REVOKE invalidates dependent AUTHORIZED_USER grants atomically. At `T <= valid_from`, the grant becomes `PRE_EFFECTIVE_CANCELLED` with `valid_to = NULL`; at `T > valid_from`, it becomes `INACTIVE` with `valid_to = T`. Automatic changes set `lifecycle_changed_at = T` and preserve guarantor history.
- Resident status transitions are only `ACTIVE → INACTIVE`, `INACTIVE → ACTIVE`, `ACTIVE → BLOCKED`, `BLOCKED → ACTIVE` and `BLOCKED → INACTIVE`; `INACTIVE → BLOCKED` is rejected. Every status command requires MANAGEMENT, the resident-management permission, a nonblank reason and an audit record.
- `INACTIVE` preserves history and is guarded: effective Memberships and direct/effective VehicleRight authority must be explicitly ended or revoked in the same command, unless an explicit Membership/OWNER action already handles a dependent grant. `BLOCKED` preserves Membership and OWNER history while atomically ending dependent AUTHORIZED_USER grants at the status-change time; grants with `T <= valid_from` become `PRE_EFFECTIVE_CANCELLED`, and grants with `T > valid_from` become `INACTIVE` with `valid_to=T`. Reactivation does not restore ended or cancelled history.
- Explicit nested actions use `HOUSEHOLD_MEMBERSHIP_MANAGE` for Membership effects and `VEHICLE_RIGHT_MANAGE` for VehicleRight effects. Status, nested relation changes and audits use the approved Resident → Apartment → Vehicle → relation lock order and commit or roll back together.

### Implemented AHR-11 VOID behavior (approved project decisions; not original NV01/ERD rules)

- `VOID` means the source relation was created in error. Membership and VehicleRight rows remain in history; only `status`, `lifecycle_changed_at` and `lifecycle_reason` change. Existing `valid_from` and `valid_to` are preserved, so VOID is not an effective-time END or REVOKE.
- Management may VOID a Membership or VehicleRight in `ACTIVE`, `INACTIVE` or `REVOKED` state. Repeated VOID and direct VehicleRight VOID from `PRE_EFFECTIVE_CANCELLED` return a relation-state conflict. Every command requires a nonblank reason and the corresponding management permission.
- Voiding a Membership or OWNER VOID-cascades AUTHORIZED_USER relations only where the existing guarantor/context and validity/lifecycle data prove that source interval. Cascade includes dependent ACTIVE/scheduled and terminal `INACTIVE`, `REVOKED` or `PRE_EFFECTIVE_CANCELLED` history; ambiguous historical source intervals are left unchanged rather than risking VOID of an unrelated grant. Parent/cascade changes and audit records share one transaction.

These VOID meanings, allowed source states, cross-module effects and the conservative handling of unprovable historical dependencies are AHR project decisions, not requirements established by the original NV01 or ERD sources.

### AHR review-remediation — AUTHORIZED_USER grant interval bounds

- The requested AUTHORIZED_USER interval must be contained within every effective guarantor source interval: the OWNER relation, and for a HOUSEHOLD_HEAD grant, both the head Membership and the OWNER Membership in the selected Apartment context.
- Intervals are half-open, so a requested `valid_to` equal to the source `valid_to` is allowed. A null/unbounded requested end is rejected when any required source interval has a finite end. The server does not shorten the request; an interval extending beyond a source returns `GUARANTOR_CHAIN_CONFLICT`.
- This is an approved AHR review-remediation decision applied to the AHR-08 grant contract, not a rule from the original NV01 or ERD sources.

### AHR review-remediation — future OWNER transfer effects

- For a future OWNER transfer at `T`, an applicable AUTHORIZED_USER grant whose `valid_from < T` stays `ACTIVE` with `valid_to=T` until the due-time processor runs. The half-open interval makes it ineffective at and after `T`, even if processing is delayed.
- The transfer transaction stores one durable pending transition with `T`, the transfer reason and source actor. At/after `T`, processing marks the grant `INACTIVE`, records `lifecycle_changed_at=T`, and writes the automatic guarantor-loss audit using the original actor/reason. Transfer state, pending effects and transfer audits commit or roll back together.
- A grant with `valid_from >= T` is immediately `PRE_EFFECTIVE_CANCELLED` with no `valid_to`; it never becomes effective. This OWNER-transfer behavior is an approved AHR review-remediation decision, not an original NV01/ERD rule. Future household-head transfer behavior is implemented separately under AHRR-05.

### AHR review-remediation — future household-head transfer effects

- For a future household-head transfer at `T`, the previous head Membership remains `ACTIVE` with `valid_to=T`, and the successor Membership starts `ACTIVE` at `T`. Their half-open intervals switch at the transfer instant.
- An applicable AUTHORIZED_USER grant with `valid_from < T` remains `ACTIVE` with `valid_to=T` and a durable pending transition until due-time processing marks it `INACTIVE` at `T`. Before processing it remains effective before `T` and ineffective at/after `T`; the transition audit retains the household-head transfer actor/reason and original guarantor/context.
- A dependent grant with `valid_from >= T` is immediately `PRE_EFFECTIVE_CANCELLED` with no `valid_to`. Membership changes, pending effects and transfer audits commit or roll back together under the resident → Apartment → Vehicle → relation lock order. This is an approved AHR review-remediation decision, not an original NV01/ERD rule.

### AHR review-remediation — automatic versus explicit effect permissions

- Automatic dependent VehicleRight changes inherit the permission of the source command that caused the authority loss; they do not require an additional VehicleRight or Household Membership permission from the actor.
- Explicit nested Membership actions require `HOUSEHOLD_MEMBERSHIP_MANAGE`; explicit nested VehicleRight actions require `VEHICLE_RIGHT_MANAGE`. For example, a Resident status command requires `RESIDENT_MANAGE` for its automatic BLOCKED guarantor effects, while supplied nested action lists require their respective owner permissions.
- This is an approved AHR review-remediation authorization decision, not an original NV01/ERD rule.

## Acceptance Scenarios

- Cư dân chính chủ có dữ liệu mặt phù hợp được đưa vào quy trình đối chiếu.
- Vợ thuộc hộ, có ủy quyền, dùng xe đứng tên chồng: kiểm tra ảnh realtime của vợ, không dùng ảnh chồng; kết quả cho phép/review theo chính sách.
- Người thân chưa đăng ký: dù biển số/thẻ khớp, thiếu hồ sơ khuôn mặt/ủy quyền thì không tự động cho phép.
- Thành viên hộ dùng hồ sơ của chính mình và xe đã được ủy quyền.

Source: survey §5; đề cương Yêu cầu 1.

## Not Specified by Approved Sources

- Các trường dữ liệu bắt buộc ngoài các trường xe được nêu; quy tắc định danh/trùng lặp.
- Ngưỡng face matching cụ thể, số lần chụp lại và quy trình duyệt kết quả xác minh.
- Quy tắc một cư dân thuộc nhiều căn hộ, số chủ hộ đồng thời, thời hạn và quy trình thay đổi membership/ủy quyền.
- Thời hạn lưu và thời điểm xóa/ẩn danh riêng cho từng loại media; khảo sát yêu cầu chính sách nhưng không nêu số ngày.
