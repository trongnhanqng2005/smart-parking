# NV04 - Kiểm soát xe cư dân ra bãi

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 3 / NV04; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3–5; `ERD.pdf` — gate/session/media/review/barrier concepts  
Implementation status: Not implemented.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — §6 Yêu cầu 3, Chức năng 04.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §3 mất vé/biển khác/mặt khác/không tìm thấy lượt/barrier; §5.1.
- `ERD.pdf` — `parking_sessions`, `gate_events`, `manual_reviews`, `barrier_actions`, `gate_event_media`, `ai_inference_results`.

## Objective

Nhận diện xe/người tại cổng ra, tìm Parking Session `OPEN` tương ứng, hiển thị bằng chứng vào–ra, đối chiếu và chốt lượt ra.

## Actors

- Nhân viên trạm gác.
- Cư dân/người lái được quyền sử dụng xe.
- Backend quyết định kết quả ra; AI cung cấp inference.
- Ban quản lý xử lý ngoại lệ cần duyệt.

## Preconditions

- Có gate lane/nhân viên đang vận hành.
- Xe dự kiến ra có Parking Session `OPEN`, hoặc sự kiện được xử lý theo ngoại lệ Manual Review.

## Main Flow

1. Chụp ảnh lúc ra, nhận plate/face và dữ liệu xe/người theo pipeline được tích hợp.
2. Tìm Parking Session `OPEN` phù hợp.
3. Hiển thị ảnh vào và ra cạnh nhau; so sánh plate, xe và khuôn mặt/thông tin liên quan.
4. Ghi Gate Event `EXIT`, dữ liệu nhận diện và quyết định.
5. Khi đã xác nhận đủ điều kiện, chốt/đóng Parking Session.
6. Yêu cầu mở barrier và ghi Barrier Action/result riêng.

## Alternative / Exception Flows

- Không tìm thấy lượt `OPEN`: tra theo plate chuẩn hóa, thẻ, khung giờ và làn; không tạo phí ước lượng khi chưa có bằng chứng; chuyển `MANUAL_REVIEW`.
- Vé/thẻ đúng nhưng plate khác: không cho ra tự động; kiểm tra ảnh vào, giấy tờ, camera và lưu biên bản.
- Mặt vào/ra không khớp: kiểm tra trường hợp người khác lấy xe, quyền sở hữu/ủy quyền và cần Ban quản lý duyệt.
- Barrier mở nhưng lượt chưa đóng: cảnh báo ngay; nhân viên xác nhận xe đã ra rồi đóng lượt có nhật ký để tránh tính phí tiếp.
- Mất vé/thẻ: tra ảnh/plate/face/thời gian và yêu cầu quản lý duyệt; phí mất vé chỉ thu nếu bảng chính sách cấu hình.

## Business Rules

- **BR-NV04-001:** Cổng ra phải tìm Parking Session `OPEN` tương ứng.
- **BR-NV04-002:** Ảnh vào-ra được hiển thị cạnh nhau để so sánh.
- **BR-NV04-003:** Không tìm thấy lượt hoặc có biển số/mặt không khớp thì không cho ra tự động theo các ngoại lệ đã duyệt.
- **BR-NV04-004:** Không có bằng chứng thì không tạo phí ước lượng.
- **BR-NV04-005:** Nếu barrier đã mở nhưng lượt chưa đóng, nhân viên xác nhận xe đã ra và đóng lượt có nhật ký.

## Data Involved

`parking_sessions`, `gate_events`, `gate_event_media`, `media_assets`, `ai_inference_results`, `manual_reviews`, `barrier_actions`, `alerts`, `audit_logs`, cùng vehicle/card/resident để đối chiếu.

## State Changes

Parking Session hợp lệ được đóng; trường hợp barrier mở nhưng xác nhận thủ công dùng `CLOSED_MANUAL`. Gate Event ghi EXIT và decision/outcome riêng. Các transition khác không được nguồn định nghĩa: **Not specified by approved sources.**

## AI Involvement

AI nhận dạng plate/face/liveness/image quality; backend so kết quả với lượt OPEN và quyền, không để inference tự cho xe ra.

## Security / Authorization

Nhân viên trạm gác xem dữ liệu cần cho ca. Ngoại lệ mất vé/không khớp mặt yêu cầu quản lý duyệt. Điều chỉnh phí chỉ bởi người có quyền và có lý do.

## Audit Requirements

Manual handling cần actor, timestamp, reason và evidence. Sự kiện barrier mở mà chưa đóng lượt cần cảnh báo và nhật ký xác nhận.

## Acceptance Scenarios

- Cư dân hợp lệ vào/ra: tìm lượt `OPEN`, đối chiếu ảnh, đóng `CLOSED`.
- Không tìm thấy lượt: tìm theo plate chuẩn hóa, card, thời gian, lane; không tính phí ước lượng; review.
- Biển đúng với thẻ nhưng khác lượt vào: không tự cho ra; kiểm tra evidence và lưu biên bản.
- Face mismatch: Manual Review, kiểm tra ủy quyền và cần quản lý duyệt.
- Barrier mở nhưng chưa đóng lượt: cảnh báo; xác nhận xe đã ra; `CLOSED_MANUAL` và nhật ký.

## Not Specified by Approved Sources

- Thứ tự vật lý chính xác giữa đóng Parking Session, lệnh barrier và xác nhận xe thực sự qua barrier trong nhánh bình thường.
- Chi tiết ngưỡng so khớp face/plate và các trạng thái trung gian ngoài enum ERD.
