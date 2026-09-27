# NV07 - Xử lý sự cố và quyết định thủ công

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 4 / NV07; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3, §4.3–4.4, §5–5.1; `ERD.pdf` — review/action/sync/alert/audit concepts  
Implementation status: Not implemented.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — §6 Yêu cầu 4, Chức năng 07.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3 exception flows; §4.3 offline; §4.4 protection/logging; §5 scenarios; §5.1 acceptance.
- `ERD.pdf` — `gate_events`, `manual_reviews`, `barrier_actions`, `parking_sessions`, `gate_event_media`, `ai_inference_results`, `alerts`, `audit_logs`.

## Objective

Giữ vận hành có kiểm soát khi ảnh/AI/API/camera gặp sự cố; cho phép review/manual handling có danh tính và lý do; hỗ trợ offline queue, resynchronization Idempotency và giữ conflict để quản lý xử lý.

## Actors

- Nhân viên trạm gác: chụp lại/điều chỉnh ảnh, thao tác event/barrier và xử lý trong phạm vi quyền.
- Ban quản lý: duyệt ngoại lệ yêu cầu quản lý, xác nhận conflict/chênh lệch theo trách nhiệm.
- WinForms desktop: phát hiện mất kết nối, lưu queue cục bộ, đồng bộ lại.
- Backend: kiểm tra, nhận event, chống trùng và ghi conflict.
- AI/camera/API: hệ thống phụ thuộc có thể không sẵn sàng.

## Preconditions

- Có Gate Event/luồng cổng cần xử lý hoặc xảy ra mất kết nối.
- Nhân viên đăng nhập; quyết định/manual action phải gắn danh tính.
- Khi offline, máy trạm có thể dùng dữ liệu tối thiểu đã tải trước; không đủ dữ liệu thì cần kiểm tra thủ công.

## Main Flow

### Ảnh hoặc inference không đạt

1. Áp dụng quality gate; ảnh mờ/cháy sáng không được so sánh ngay.
2. Điều chỉnh camera/chụp lại, tăng tương phản/deskew theo đề cương.
3. Nếu không đạt hoặc AI không nhận dạng, chuyển nhân viên xử lý; kết quả AI ban đầu được giữ.

### Manual Review

1. Ghi Gate Event, evidence và lý do review.
2. Nhân viên/manager kiểm tra ảnh vào-ra, biển, thẻ, giấy tờ, thời gian và quyền theo loại ngoại lệ.
3. Người có thẩm quyền ghi APPROVE/REJECT, danh tính, thời gian, reason và note.
4. Barrier Action được ghi riêng với trigger source, người yêu cầu và kết quả.

### Offline / Resynchronization

1. Desktop phát hiện không kết nối API/database trong khoảng thời gian được cấu hình → `OFFLINE_MODE`.
2. Tải bộ dữ liệu tối thiểu **đã mã hóa**: thẻ/xe cư dân ACTIVE, cấu hình phí và dải số lượt tạm.
3. Lưu cục bộ ảnh, thời gian, biển số, thẻ, thao tác nhân viên và mã lượt tạm; không xóa trước khi đồng bộ.
4. Trường hợp thiếu dữ liệu phải kiểm tra thủ công; không giả định hợp lệ chỉ vì mất mạng.
5. Khi có kết nối, gửi theo thứ tự thời gian với khóa chống trùng; server trả mã lượt chính thức.
6. Nếu có conflict về `OPEN` session/payment, giữ `SYNC_CONFLICT` cho quản lý, không tự ghi đè.

## Alternative / Exception Flows

- Mất vé/thẻ: tra plate, ảnh xe, face vào/ra và thời gian; manager duyệt; phí mất vé chỉ thu nếu chính sách có cấu hình.
- Plate khác nhưng vé đúng: không cho ra tự động; kiểm tra ảnh vào, giấy tờ/camera, lưu biên bản.
- Face mismatch: kiểm tra người khác lấy xe và quyền sở hữu/ủy quyền; cần quản lý duyệt.
- Không tìm thấy OPEN: tra theo plate chuẩn hóa, card, khung giờ, lane; không ước lượng phí khi thiếu bằng chứng.
- AI không rõ loại xe: nhân viên chọn loại trước khi tính phí và source `MANUAL`.
- Khách thiếu tiền/payment lỗi: Charge `PAYMENT_PENDING`, không gán `PAID` giả.
- Barrier đã mở nhưng lượt chưa đóng: alert ngay; nhân viên xác nhận xe ra và đóng `CLOSED_MANUAL` có nhật ký.

## Business Rules

- **BR-NV07-001:** Offline event/evidence không bị xóa trước khi sync; đồng bộ theo thứ tự thời gian bằng khóa chống trùng.
- **BR-NV07-002:** Thiếu dữ liệu khi offline không đồng nghĩa được phép vào/ra.
- **BR-NV07-003:** Xung đột lượt OPEN/payment được giữ `SYNC_CONFLICT`; không tự ghi đè.
- **BR-NV07-004:** Mọi xử lý thủ công ghi tài khoản, thời gian, lý do và ảnh bằng chứng.
- **BR-NV07-005:** Mọi quyết định/action thủ công giữ riêng khỏi kết quả AI; không tự mở barrier ở điều kiện cấm tại §5.1.
- **BR-NV07-006:** Điều chỉnh phí chỉ người có quyền, kèm lý do và hiển thị evidence/công thức.

## Data Involved

`gate_events`, `manual_reviews`, `barrier_actions`, `parking_sessions`, `gate_event_media`, `media_assets`, `ai_inference_results`, `alerts`, `audit_logs`, `work_shifts`, `charges`, `payments`.

## State Changes

ERD có `operating_mode` ONLINE/OFFLINE, `sync_status`, `parking_session_status.SYNC_CONFLICT`, `manual_review_decision.APPROVE/REJECT`, outcomes event, barrier result và payment pending. Survey nêu các kết quả cụ thể như `MANUAL_REVIEW`, `PAYMENT_PENDING`, `CLOSED_MANUAL`, `OFFLINE_OPEN`. Tất cả enum literal như được lưu trong ERD giữ nguyên; transition chi tiết khác **Not specified by approved sources**.

## AI Involvement

AI/camera/API có thể unavailable hoặc kết quả partial/fail; offline/manual path không đồng nghĩa AI đã xác minh. Nhân viên quyết định trong quyền được cấp; backend lưu nguồn AI/manual và quyết định.

## Security / Authorization

- Least privilege: nhân viên trạm gác chỉ xem dữ liệu cần cho ca; Ban quản lý sửa hồ sơ và duyệt ngoại lệ.
- Sensitive data được mã hóa khi lưu/truyền; kiểm soát media.
- Dữ liệu demo dùng tình nguyện/giả lập; không đẩy CCCD/face thật tới AI công cộng khi chưa có cơ sở/chấp thuận.

## Audit Requirements

Mọi xem/sửa/xóa hồ sơ, mở barrier thủ công, đổi phí, điều chỉnh thanh toán, review, conflict resolution đều cần audit theo phạm vi nguồn. Mọi manual action có actor/time/reason/evidence.

## Acceptance Scenarios

- Ảnh face cháy sáng: không so sánh ngay; điều chỉnh/chụp lại; quá số lần chuyển REVIEW.
- OCR sai ký tự: nhiều frame + card + profile; sửa có lưu AI original.
- Khách mất vé: evidence, manager review, lost-ticket fee chỉ khi policy cấu hình.
- Offline entry: tạm ID và local queue, sync Idempotency, không duplicate.
- Conflict về OPEN/payment: giữ SYNC_CONFLICT và chờ manager; không ghi đè.
- Hệ thống khôi phục không duplicate session/payment và không mất ảnh.

## Not Specified by Approved Sources

- Timeout phát hiện offline “trong thời gian cấu hình” và cách tải/refresh cache chi tiết.
- Retry count/interval, định dạng local store, encryption key lifecycle và protocol sync.
- Quy trình kết thúc conflict cụ thể sau khi manager review.
