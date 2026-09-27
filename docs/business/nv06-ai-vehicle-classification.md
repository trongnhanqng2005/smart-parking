# NV06 - Phân loại phương tiện bằng AI

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — §5–7, Yêu cầu 3–4 / NV03, NV06; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3, §4.2, §5; `ERD.pdf` — `ai_inference_results`, `decision_policy_versions`  
Implementation status: Not implemented; `ai-service/README.md` states inference/model logic is not included.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 3/4, Chức năng 03/06; môi trường AI §7.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3 AI không xác định loại xe; §4.2 AI metrics; §5 ảnh kém/OCR sai; §5.1 gate safety.
- `ERD.pdf` — `ai_inference_results`, `decision_policy_versions`, `face_templates`, `identity_verifications`.

## Objective

Cung cấp inference hỗ trợ nghiệp vụ gồm nhận diện biển/xe, OCR, phân loại phương tiện và các kết quả face/liveness/image quality theo luồng yêu cầu; lưu confidence, trạng thái, thời gian xử lý và model versions.

## Actors

- FastAPI AI service thực hiện xử lý.
- WinForms/backend gửi dữ liệu theo hợp đồng tích hợp khi được thiết kế.
- Backend dùng inference trong business decision.
- Nhân viên xử lý thủ công khi cần.

## Preconditions

- Ảnh/camera hoặc media mô phỏng đã được thu nhận.
- Dịch vụ AI/model phù hợp sẵn sàng; nếu không thì flow NV07.

## Main Flow

1. Nhận dữ liệu ảnh cho nghiệp vụ đã yêu cầu.
2. Thực hiện phát hiện phương tiện/biển số (YOLO), OCR biển, vehicle classification, image quality và face/liveness khi luồng cần.
3. Trả kết quả, confidence và thông tin model/processing.
4. Backend ghi nhận inference attempt và dùng kết quả làm dữ liệu đầu vào đối chiếu nghiệp vụ.
5. Backend ra quyết định cổng; nhân viên có thể resolve thủ công theo flow tương ứng.

## Alternative / Exception Flows

- Ảnh mờ/cháy sáng: quality gate; tăng tương phản/deskew, điều chỉnh camera hoặc chụp lại; nếu không xử lý được chuyển nhân viên.
- AI không xác định loại xe: nhân viên chọn loại xe trước khi tính phí và lưu nguồn `MANUAL`.
- OCR sai ký tự: đối chiếu nhiều frame/thẻ/hồ sơ; nhân viên sửa nhưng giữ nguyên AI result ban đầu.
- AI inference `PARTIAL`/`FAILED` hoặc mất kết nối: flow xử lý sự cố/offline NV07.

## Business Rules

- **BR-NV06-001:** Hệ thống yêu cầu YOLO, OCR, face matching, liveness và vehicle classification theo phạm vi nêu ở đề cương.
- **BR-NV06-002:** AI trả nhận diện/kết quả và độ tin cậy để hỗ trợ áp giá/đối chiếu.
- **BR-NV06-003:** AI inference không phải business authorization; cổng đối chiếu và quyết định theo luồng NV03/NV04/NV05.
- **BR-NV06-004:** Khi người dùng sửa kết quả OCR/vehicle, phải lưu kết quả AI ban đầu và nguồn resolve `MANUAL`.

## Data Involved

`ai_inference_results`, `decision_policy_versions`, `media_assets`, `gate_event_media`, `face_templates`, `identity_verifications`, `vehicle_families`, `vehicle_categories`, `gate_events`.

## State Changes

ERD định nghĩa `ai_inference_status` với `SUCCESS`, `PARTIAL`, `FAILED`; nguồn không mô tả transition chi tiết giữa states: **Not specified by approved sources.** `resolution_source` cho kết quả resolve có các literal được duyệt trong [enums](../database/enums.md).

## AI Involvement

AI có thể thực hiện:

- YOLO phát hiện phương tiện/biển số.
- OCR nhận dạng ký tự plate.
- Vehicle classification (xe máy, ô tô, xe điện...) và confidence.
- Face matching và liveness cho nghiệp vụ liên quan.
- Đánh giá image quality.

Backend đối chiếu inference với hồ sơ/thẻ/quyền/gói/chính sách và quyết định cho qua. Ngưỡng cụ thể nằm ở `decision_policy_versions` trong ERD; giá trị ngưỡng được cấu hình cụ thể **Not specified by approved sources**.

## Security / Authorization

Ảnh CCCD/face và media nhạy cảm không gửi lên dịch vụ AI công cộng khi chưa có cơ sở và chấp thuận phù hợp. Dữ liệu nhạy cảm được mã hóa khi lưu/truyền theo khảo sát.

## Audit Requirements

Giữ AI inference gốc/model version; thao tác nhân viên sửa/resolve phải có user, thời gian, lý do và evidence theo yêu cầu manual handling.

## Acceptance Scenarios

- AI classification trả vehicle family/confidence hỗ trợ áp đúng giá.
- Không xác định loại xe: nhân viên chọn trước tính phí, source `MANUAL`.
- OCR sai một ký tự: xác minh nhiều frame/thẻ/hồ sơ, giữ AI original, cho phép sau xác minh.
- Ảnh mặt cháy sáng: không so sánh ngay; chỉnh/chụp lại; quá số lần chuyển REVIEW.
- Kết quả inference không tự mở barrier khi liveness fail hoặc dữ liệu mâu thuẫn rõ.

## Not Specified by Approved Sources

- Model/weights cụ thể, format ảnh, endpoint, timeout/retry và giao thức giữa caller và FastAPI.
- Ngưỡng confidence thực tế, chuẩn đo lường/benchmark và số lần retry.
- Vị trí thực thi từng phép AI (Desktop hay backend) trong topology tích hợp.
