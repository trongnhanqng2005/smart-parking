# NV02 - Quản lý thẻ, gói gửi xe 30 ngày và thanh toán

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 2 / NV02; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §1, §4.1, §4.2, §5; `ERD.pdf` — card/pricing/subscription/billing tables  
Implementation status: Not implemented.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — §6 Yêu cầu 2; Chức năng 02.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §1 giá cư dân 30 ngày; §4.1 thanh toán; §4.2 báo cáo gói; §5 gói vừa hết hạn.
- `ERD.pdf` — `cards`, `card_assignments`, pricing tables, `parking_subscriptions`, `charges`, `charge_items`, `payments`.

## Objective

Quản lý việc cấp/gán thẻ cư dân, cấu hình bảng giá, đăng ký/gia hạn gói gửi xe 30 ngày, xử lý khóa/thay thẻ và ghi nhận khoản phải thu, thanh toán, hóa đơn/biên nhận.

## Actors

- Ban quản lý: cấu hình giá, quản lý thẻ/hồ sơ, duyệt hoàn/hủy/điều chỉnh theo quyền.
- Nhân viên được phân quyền: ghi nhận cấp thẻ hoặc payment, tiền mặt gắn ca.
- Cư dân: người đăng ký/gia hạn gói và thanh toán.
- Hệ thống thanh toán điện tử: kết quả chuyển khoản/QR; chi tiết kết nối chưa có contract.

## Preconditions

- Xe cư dân được đăng ký trong hệ thống.
- Có bảng giá phù hợp đang áp dụng.
- Thẻ được cấp/gán nếu nghiệp vụ yêu cầu.

## Main Flow

1. Cấu hình bảng giá theo loại xe và loại khách; các mức cư dân tháng và khách được materialize trong NV05.
2. Cấp và gán thẻ cho cư dân.
3. Chọn giá gói 30 ngày cho xe và lập gói đăng ký/gia hạn.
4. Tạo Charge từ gói, lưu phiên bản bảng giá/công thức áp dụng và chi tiết thành phần.
5. Hiển thị số tiền, chi tiết tính và phương thức thanh toán; nhận kết quả tiền mặt/chuyển khoản/QR.
6. Chỉ khi payment thành công mới chuyển Charge sang `PAID`, phát hành biên nhận và kích hoạt gói.
7. Khi mất thẻ/vi phạm, tạm khóa; khi thay thế ghi nhận thẻ mới và liên hệ với thẻ cũ.

## Alternative / Exception Flows

- Thẻ mất hoặc vi phạm: tạm khóa theo yêu cầu NV02.
- Thay thẻ: phát hành thẻ thay thế; ERD có `replaced_card_id`.
- Payment thất bại/chưa hoàn tất: Charge giữ `PAYMENT_PENDING`; không ghi nhận `PAID` giả.
- Hoàn/hủy/điều chỉnh: yêu cầu quyền quản lý, lý do và liên kết giao dịch gốc.
- Gói tháng vừa hết hạn: cảnh báo gia hạn; không tự coi là khách nếu chưa có lựa chọn nghiệp vụ.

## Business Rules

- **BR-NV02-001:** Gói cư dân là gói **30 ngày**; tài liệu không đổi cách hiểu thành tháng lịch.
- **BR-NV02-002:** Mức gói áp dụng cho **một chiếc xe**; nhiều xe tính theo số lượng xe tương ứng.
- **BR-NV02-003:** Giá cư dân theo khảo sát §1:

| Loại xe | Mức phí theo survey (ghi /tháng; mục §1 áp dụng 30 ngày) |
|---|---:|
| Xe đạp, xe đạp điện | 100.000đ/tháng |
| Xe máy, xe điện thông thường | 150.000đ/tháng |
| Xe máy phân khối lớn | 300.000đ/tháng |
| Ô tô từ 4–5 chỗ | 1.500.000đ/tháng |
| Ô tô từ 6–7 chỗ | 1.700.000đ/tháng |
| Ô tô từ 8–9 chỗ | 1.800.000đ/tháng |

- **BR-NV02-004:** Ví dụ: hộ có hai xe máy thường và một ô tô 4 chỗ trả `2 × 150.000 + 1.500.000 = 1.800.000đ/tháng`.
- **BR-NV02-005:** Khi thanh toán thành công mới chuyển `PAID`, phát hành biên nhận và kích hoạt gói.
- **BR-NV02-006:** Tiền mặt gắn ca nhân viên; giao dịch điện tử có mã đối soát.
- **BR-NV02-007:** Hoàn/hủy/điều chỉnh cần quyền quản lý, lý do và liên kết giao dịch gốc.

## Data Involved

`cards`, `card_assignments`, `pricing_versions`, `monthly_rates`, `parking_subscriptions`, `charges`, `charge_items`, `payments`, `work_shifts`, `users`, `vehicles`. Xem [data dictionary](../database/data-dictionary.md).

## State Changes

ERD có `card_status`, `subscription_status`, `charge_status`, `payment_status`. Nguồn xác định payment thành công dẫn đến `PAID` và kích hoạt gói; lỗi/khách không đủ tiền giữ `PAYMENT_PENDING`; thẻ có thể bị tạm khóa, thay/hủy. Các chuyển trạng thái còn lại không được nguồn xác định đầy đủ: **Not specified by approved sources.**

## AI Involvement

AI không tham gia ghi nhận Charge/Payment hoặc kích hoạt gói trong quy tắc NV02.

## Security / Authorization

- Thay đổi giá và hoàn/hủy/điều chỉnh chỉ do người có quyền; hoàn/hủy/điều chỉnh cần quyền quản lý.
- Giao dịch tiền mặt liên kết ca/nhân viên; giao dịch điện tử phải có external reconciliation reference.

## Audit Requirements

Thay đổi phí và điều chỉnh thanh toán phải audit log; thao tác xem/sửa/xóa hồ sơ cũng có audit log theo §4.4.

## Acceptance Scenarios

- Hai xe máy thường và một ô tô 4 chỗ: 1.800.000đ cho gói theo khảo sát.
- Payment chưa thành công không kích hoạt subscription và không chuyển Charge `PAID`.
- Giao dịch điện tử lưu mã đối soát; tiền mặt liên kết ca.
- Gói vừa hết hạn không tự đổi thành lượt khách; cần lựa chọn nghiệp vụ.

## Not Specified by Approved Sources

- Mốc tính bắt đầu/kết thúc, gia hạn sớm hoặc xử lý các gói chồng thời gian.
- Quy trình thẻ khách cấp/thu hồi; thời gian mở khóa/thay thẻ ngoài trạng thái ERD.
- Tích hợp cụ thể và cách xác nhận tự động chuyển khoản/QR.
