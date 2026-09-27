# NV05 - Khách vãng lai gửi xe và tính phí

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 2–4 / NV02, NV05; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §2.1–2.3, §3, §4.1, §5; `ERD.pdf` — visitor pricing/session/card/billing tables  
Implementation status: Not implemented.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — §6 Yêu cầu 2–3, Chức năng 02/05.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §2 toàn bộ giá và ví dụ; §3 ngoại lệ; §4.1 thanh toán; §5 khách mất vé/thời lượng.
- `ERD.pdf` — `cards`, `parking_sessions`, `gate_events`, visitor pricing tables, `charges`, `charge_items`, `payments`, `manual_reviews`.

## Objective

Cấp thẻ khách tại cổng, ghi nhận lượt đỗ và bằng chứng; khi ra tính phí theo loại xe/thời gian/bảng giá, xác nhận thu tiền và đóng lượt.

## Actors

- Nhân viên trạm gác: cấp thẻ, ghi nhận xe/lượt và thanh toán tiền mặt trong ca.
- Khách vãng lai: gửi xe và thanh toán.
- Ban quản lý: xử lý ngoại lệ/điều chỉnh có quyền.
- AI: phát hiện/phân loại xe, cung cấp confidence; không tự xác lập phí cuối hoặc cho xe qua.

## Preconditions

- Bảng giá khách có hiệu lực cho loại xe cần tính.
- Cổng và thẻ khách sẵn sàng.
- Có thể xác định loại xe; nếu AI không xác định, nhân viên chọn loại xe trước khi tính và lưu nguồn `MANUAL`.

## Main Flow

1. Cấp thẻ khách tại cổng; ghi nhận thẻ, xe/biển số, loại xe và bằng chứng vào.
2. Tạo Gate Event và Parking Session cho lượt khách; lượt vào được ghi nhận `OPEN`.
3. Khi khách ra, nhận dạng xe, tìm lượt mở và xác định thời lượng từ mốc vào/ra.
4. Tính phí theo giá khách và lưu phiên bản giá/chi tiết công thức áp dụng.
5. Hiển thị số tiền, chi tiết và phương thức; ghi kết quả thu tiền.
6. Chỉ khi thanh toán thành công mới chuyển Charge `PAID`, phát hành biên nhận và cho đóng lượt.
7. Đóng Parking Session và ghi event/action cổng tương ứng.

## Alternative / Exception Flows

- AI không xác định loại xe: nhân viên chọn loại xe, nguồn phân loại lưu `MANUAL`, tiếp tục tính phí.
- Khách không đủ tiền/lỗi payment: giữ `PAYMENT_PENDING`; không đánh dấu `PAID` giả; áp dụng quy trình Ban quản lý.
- Mất vé/thẻ: tra biển số, ảnh xe, mặt vào/ra và thời gian; yêu cầu quản lý duyệt; phí mất vé chỉ thu nếu bảng chính sách có cấu hình.
- Không tìm được lượt OPEN: tra plate chuẩn hóa, thẻ, khung giờ, làn; không phí ước lượng khi thiếu bằng chứng; `MANUAL_REVIEW`.
- Tranh chấp thời gian/phí: hiển thị ảnh vào/ra, mốc thời gian, chi tiết công thức; điều chỉnh chỉ người có quyền và có lý do.
- Biển số hoặc khuôn mặt không khớp: không tự động cho ra; xác minh và lưu biên bản theo flow ngoại lệ.

## Business Rules

### Giá xe máy

- Ban ngày **06:00–17:59:** 5.000đ/lượt.
- Ban đêm **18:00–05:59:** 8.000đ/lượt.
- Qua đêm: 10.000đ/chu kỳ; cách tính nêu là tổng số giờ / 24 (làm tròn).
- **Giữ nguyên survey example:** 08:00–20:00 cùng ngày được phân loại **ban đêm**, 8.000đ.

### Giá ô tô

- 4 giờ đầu: 40.000đ/lượt.
- Trên 4 giờ: mỗi 2 giờ tăng thêm 20.000đ/lượt; phần chưa đủ 2 giờ làm tròn thành một lần.
- Mỗi lần qua mốc 00:00 tính một đêm; phí tối thiểu qua đêm = số đêm × 100.000đ.
- Phí cuối cùng lấy mức cao hơn giữa phí theo thời gian và phí tối thiểu qua đêm.

### Giá xe đạp và xe đạp điện

- Ban ngày **06:00–17:59:** 2.000đ/lượt.
- Ban đêm **18:00–05:59:** 3.000đ/lượt.
- Qua đêm: 5.000đ/chu kỳ; cách tính chu kỳ giống xe máy vãng lai.

### Payment và evidence

- Charge giữ pricing version/công thức được dùng; successful payment mới thành `PAID`, phát hành biên nhận và cho đóng lượt.
- Tiền mặt gắn ca nhân viên; payment điện tử có mã đối soát.
- Không tìm thấy lượt thì không tạo phí ước lượng khi chưa có bằng chứng.

## Data Involved

`cards`, `card_assignments` nếu áp dụng, `parking_sessions`, `gate_events`, `gate_event_media`, `media_assets`, `vehicle_families`, `vehicle_categories`, `visitor_period_rates`, `visitor_car_rate_rules`, `pricing_versions`, `charges`, `charge_items`, `payments`, `work_shifts`, `manual_reviews`, `barrier_actions`, `alerts`.

## State Changes

Vào tạo Parking Session `OPEN`; successful payment chuyển Charge thành `PAID`; thất bại/thiếu tiền giữ `PAYMENT_PENDING`; lượt đóng sau điều kiện thanh toán theo quy trình. Review ngoại lệ ghi `MANUAL_REVIEW`; ERD định nghĩa các enum liên quan. Các transition payment retry/partial không được định nghĩa: **Not specified by approved sources.**

## AI Involvement

AI phát hiện biển/xe, OCR, phân loại và confidence. Khi không xác định loại xe, nhân viên chọn và lưu nguồn `MANUAL`. Business backend sử dụng dữ liệu đã resolve và áp bảng giá; AI không tự tính thu hoặc quyết định quyền barrier.

## Security / Authorization

Nhân viên thao tác theo ca/quyền; Ban quản lý duyệt mất vé và các điều chỉnh. Điều chỉnh/hoàn/hủy có lý do, người thực hiện và giao dịch gốc khi có.

## Audit Requirements

Mất vé, mở barrier thủ công, điều chỉnh Charge/Payment, sửa kết quả nhận diện cần lưu operator, thời gian và lý do; ảnh vào/ra được giữ làm bằng chứng theo chính sách dữ liệu.

## Acceptance Scenarios

### Xe máy — nguyên văn các mốc trong survey §2.1

| Thời gian gửi | Phân loại/cách tính | Tổng phí |
|---|---|---:|
| 08:00–12:00 cùng ngày | Ban ngày; 1 lượt ban ngày | 5.000đ |
| 08:00–20:00 cùng ngày | Ban đêm, không qua ngày mới; 1 lượt ban đêm | 8.000đ |
| 18:00–23:00 cùng ngày | Ban đêm, không qua ngày mới; 1 lượt ban đêm | 8.000đ |
| 01:00–05:00 cùng ngày | Ban đêm, không qua ngày mới; 1 lượt ban đêm | 8.000đ |
| 22:00 ngày 10–02:00 ngày 11 | Qua 00:00; 1 chu kỳ qua đêm | 10.000đ |
| 08:00 ngày 10–07:00 ngày 11 | Qua 00:00, dưới 24 giờ; 1 chu kỳ | 10.000đ |
| 08:00 ngày 10–08:00 ngày 11 | Đúng 24 giờ; 1 chu kỳ | 10.000đ |
| 08:00 ngày 10–08:01 ngày 11 | 24 giờ 1 phút; 2 chu kỳ | 20.000đ |
| 08:00 ngày 10–10:00 ngày 11 | 26 giờ; 2 chu kỳ | 20.000đ |
| 08:00 ngày 10–08:00 ngày 12 | Đúng 48 giờ; 2 chu kỳ | 20.000đ |
| 08:00 ngày 10–08:01 ngày 12 | 48 giờ 1 phút; 3 chu kỳ | 30.000đ |
| 08:00 ngày 10–10:00 ngày 12 | 50 giờ; 3 chu kỳ | 30.000đ |

### Ô tô — ví dụ survey §2.2 và biên nghiệm thu §5.1

| Kịch bản | Phí thời gian | Phí qua đêm | Kết quả |
|---|---:|---:|---:|
| Đúng 4 giờ | 40.000đ | 0đ | 40.000đ |
| 4 giờ 1 phút | 60.000đ | 0đ | 60.000đ |
| 3 giờ | 40.000đ | 0đ | 40.000đ |
| 5 giờ | 60.000đ | 0đ | 60.000đ |
| 7 giờ | 80.000đ | 0đ | 80.000đ |
| 23:00–02:00 hôm sau (3 giờ) | 40.000đ | 100.000đ | 100.000đ |
| 20:00–07:00 hôm sau (11 giờ) | 120.000đ | 100.000đ | 120.000đ |
| 08:00 ngày 10–10:00 ngày 11 (26 giờ, qua 1 đêm) | 260.000đ | 100.000đ | 260.000đ |
| 20:00 ngày 10–07:00 ngày 12 (35 giờ, qua 2 đêm) | 360.000đ | 200.000đ | 360.000đ |

### Survey acceptance cases

- Kiểm tra mốc 4 giờ, 4 giờ 1 phút, đúng 24 giờ, 24 giờ 1 phút và nhiều lần qua 00:00 theo survey §5.1.
- Khách xe máy gửi 26 giờ: `2 × 10.000 = 20.000đ`.
- Khách ô tô 23:00–02:00: max(40.000đ, 100.000đ) = 100.000đ.
- Ảnh/OCR không đạt: xử lý theo review; không mất kết quả AI gốc khi nhân viên xác minh.
- Khách mất vé: tra evidence, quản lý duyệt, phí mất vé chỉ áp dụng nếu chính sách có cấu hình.

## Not Specified by Approved Sources

- Quy tắc tổng quát cho toàn bộ lượt ban ngày giao ban đêm ngoài các mức giờ và ví dụ đã duyệt.
- Phương thức xác nhận chuyển khoản/QR và xử lý thanh toán một phần.
- Quy tắc tái sử dụng/thu hồi thẻ khách và mốc thời gian ghi nhận xe thực sự qua barrier.
