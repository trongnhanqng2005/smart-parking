# NV03 - Kiểm soát xe cư dân vào bãi

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 3 / NV03; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3, §4.3, §5; `ERD.pdf` — gate/session/AI/media concepts  
Implementation status: Not implemented.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — §5 nghiệp vụ đặc trưng; §6 Yêu cầu 3, Chức năng 03.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3 ngoại lệ; §4.3 offline; §5 cư dân chính chủ/người nhà/gói hết hạn/ảnh kém/OCR; §5.1 nghiệm thu.
- `ERD.pdf` — `gate_lanes`, `gate_events`, `parking_sessions`, `ai_inference_results`, `gate_event_media`, `barrier_actions`.

## Objective

Kiểm soát xe cư dân tại làn vào bằng capture, card scan, nhận dạng và đối chiếu an toàn; chỉ tạo lượt `OPEN` và yêu cầu mở barrier theo kết quả quyết định hợp lệ.

## Actors

- Nhân viên trạm gác vận hành WinForms.
- Cư dân/người được ủy quyền sử dụng xe.
- Backend xử lý authorization và gate decision.
- AI service tạo kết quả nhận diện; không tự quyết định quyền vào.

## Preconditions

- Có làn vào hoạt động và nhân viên/ca phù hợp.
- Hồ sơ xe, người, thẻ và gói cư dân có thể được tra cứu; offline chỉ có bộ dữ liệu tối thiểu đã tải trước.
- Camera/capture hoặc media mô phỏng sẵn sàng; nếu không, chuyển flow sự cố NV07.

## Main Flow

1. Trạm gác chụp ảnh biển số/xe và lái xe.
2. Hệ thống AI phát hiện biển số bằng YOLO, OCR ký tự, phân loại xe và thực hiện face/liveness theo khả năng đã tích hợp; trả kết quả và confidence.
3. Nhân viên quét thẻ.
4. Backend đối chiếu thẻ, biển số/xe, loại xe, khuôn mặt người lái, quyền sử dụng xe, tình trạng gói và liveness.
5. Ghi Gate Event, kết quả nhận diện và quyết định.
6. Khi đủ điều kiện, tạo Parking Session `OPEN` và yêu cầu mở barrier; ghi riêng Barrier Action/kết quả.

Gate Event lưu lần xử lý ở cổng; Parking Session biểu diễn lượt đỗ. Hai khái niệm và bảng dữ liệu tách biệt.

## Alternative / Exception Flows

- Ảnh biển/mặt kém: không so sánh ngay; điều chỉnh/chụp lại; quá số lần thì nhân viên xử lý.
- OCR sai ký tự: đối chiếu nhiều khung hình, thẻ và hồ sơ; nhân viên sửa nhưng giữ kết quả AI gốc.
- Liveness thất bại hoặc dữ liệu xe/người mâu thuẫn rõ: không tự mở barrier.
- Người nhà chưa có hồ sơ khuôn mặt/ủy quyền: không tự cho phép.
- Gói tháng vừa hết hạn: cảnh báo gia hạn; không tự coi là khách nếu chưa có lựa chọn nghiệp vụ.
- API/AI/camera mất kết nối: thực hiện dự phòng offline/manual theo NV07.
- Đã có lượt OPEN: không tạo lượt OPEN thứ hai cho cùng xe nếu chưa xử lý lượt trước.

## Business Rules

- **BR-NV03-001:** Cổng vào chụp ảnh biển số và lái xe, nhận diện plate/OCR, kiểm tra liveness và quét thẻ (đề cương Yêu cầu 3).
- **BR-NV03-002:** Đối chiếu thông tin an toàn trước quyết định vào.
- **BR-NV03-003:** Thành viên hộ được xác minh bằng chính hồ sơ mặt của họ và chỉ dùng xe đã ủy quyền.
- **BR-NV03-004:** Không tự mở barrier khi liveness thất bại hoặc dữ liệu xe/người mâu thuẫn rõ ràng.
- **BR-NV03-005:** Không tạo hai Parking Session `OPEN` cho cùng một xe nếu chưa xử lý lượt trước.

## Data Involved

`gate_stations`, `gate_lanes`, `work_shifts`, `gate_events`, `parking_sessions`, `cards`, `card_assignments`, `residents`, `vehicles`, `vehicle_resident_relations`, `parking_subscriptions`, `decision_policy_versions`, `ai_inference_results`, `media_assets`, `gate_event_media`, `barrier_actions`, `alerts`, `audit_logs`.

## State Changes

Entry được phép tạo Parking Session `OPEN`; Gate Event ghi `ENTRY`, decision/outcome và mode; ERD định nghĩa action barrier riêng. Quy tắc chuyển đầy đủ giữa `PENDING`, `ALLOWED`, `REJECTED` và các kết quả thiết bị ngoài các điều kiện nêu trên: **Not specified by approved sources.**

## AI Involvement

YOLO phát hiện plate/vehicle; OCR đọc plate; vehicle classification trả loại/confidence; face matching/liveness và image quality hỗ trợ đối chiếu. Backend đối chiếu quyền thành viên/xe/thẻ/gói và ra business decision. AI inference không phải authorization.

## Security / Authorization

Nhân viên trạm gác chỉ xem dữ liệu cần cho ca; Ban quản lý có quyền sửa hồ sơ và duyệt ngoại lệ. Không tự mở barrier ở điều kiện bị cấm nêu trong khảo sát.

## Audit Requirements

Mỗi xử lý thủ công lưu tài khoản, thời gian, lý do và ảnh bằng chứng. Gate history phải truy ngược được dữ liệu vào, AI decision, operator decision và payment liên quan khi có.

## Acceptance Scenarios

- Cư dân chính chủ: thẻ, biển số, gói tháng, liveness và khuôn mặt hợp lệ; tạo `OPEN`.
- Vợ dùng xe đứng tên chồng: có ủy quyền, so ảnh người vợ; kết quả cho phép/review theo chính sách.
- Người thân chưa đăng ký: không tự động cho phép dù thẻ/biển đúng.
- Liveness thất bại, không được tự mở barrier.
- Mất mạng lúc vào: số lượt tạm/local queue theo NV07, đồng bộ idempotent sau đó.

## Not Specified by Approved Sources

- Ngưỡng confidence cụ thể và ma trận chi tiết mọi tổ hợp thẻ/plate/face/subscription.
- Cơ chế vật lý/camera/barrier cụ thể; số lần chụp lại.
- API contract, bên gọi AI và định dạng trao đổi.
