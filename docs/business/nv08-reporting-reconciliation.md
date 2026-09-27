# NV08 - Tra cứu, báo cáo doanh thu và đối soát

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 5 / NV08; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §4.1–4.2, §4.4; `ERD.pdf` — `work_shifts`, event, billing, alert, audit tables  
Implementation status: Not implemented.

## Source

- `CNTT_KLCN101_Tran Van Tho.md` — §6 Yêu cầu 5, Chức năng dashboard/tra cứu/báo cáo.
- `Ket_Qua_Khao_Sat_Bai_Xe.md` — §4.1 chốt ca; §4.2 chỉ tiêu; §4.4 phân quyền/audit.
- `ERD.pdf` — `parking_sessions`, `gate_events`, media, charge/payment, `work_shifts`, `alerts`, `audit_logs`.

## Objective

Ban quản lý tra cứu lịch sử chi tiết và vận hành bãi; xem dashboard, báo cáo doanh thu/đối soát, lượt đỗ, nhận dạng và ngoại lệ.

## Actors

- Ban quản lý: xem báo cáo, duyệt/xác nhận ca và ngoại lệ trong phạm vi quyền.
- Nhân viên trạm gác: đối soát và bàn giao tiền mặt/cuối ca.

## Preconditions

- Events, sessions, media, pricing, Charge/Payment và shift đã được ghi nhận.
- Người dùng có quyền xem dữ liệu tương ứng; nhân viên bị giới hạn dữ liệu cần cho ca.

## Main Flow

1. Tra cứu lịch sử theo tiêu chí vận hành, xem ảnh vào/ra, phí và người duyệt.
2. Theo dõi dashboard vận hành bãi xe.
3. Tổng hợp doanh thu theo các chiều và chỉ tiêu được survey nêu.
4. Cuối ca tổng hợp tiền mặt, điện tử, lượt miễn/điều chỉnh và chênh lệch.
5. Nhân viên bàn giao; Ban quản lý xác nhận ca.
6. Xem các giao dịch điều chỉnh, mở barrier thủ công, mất vé và chênh lệch cuối ca.

## Alternative / Exception Flows

- Payment pending, hoàn/hủy/điều chỉnh và sync conflict cần được phân biệt trong báo cáo; không tính khoản pending như payment thành công.
- Lượt ngoại lệ/review/reject phải có nguyên nhân và actor để tra cứu.
- Chênh lệch ca được tổng hợp và chuyển cho quy trình bàn giao/xác nhận; công thức giải quyết chênh lệch không được nguồn xác định.

## Business Rules

- **BR-NV08-001:** Ban quản lý tra cứu chi tiết ảnh vào/ra, phí và người duyệt.
- **BR-NV08-002:** Chỉ tiêu gồm doanh thu ngày/tháng/ca, loại xe, loại khách, phương thức thanh toán.
- **BR-NV08-003:** Báo cáo gói 30 ngày mới/gia hạn/hết hạn và doanh thu theo nhóm phương tiện.
- **BR-NV08-004:** Báo cáo lượt vào/ra, xe đang trong bãi, thời gian gửi trung bình, lượt quá 24 giờ.
- **BR-NV08-005:** Báo cáo tỷ lệ nhận dạng biển thành công, tỷ lệ ảnh mặt đạt chất lượng, số ca REVIEW/REJECT và nguyên nhân.
- **BR-NV08-006:** Báo cáo giao dịch điều chỉnh, mở barrier thủ công, mất vé và chênh lệch cuối ca.
- **BR-NV08-007:** Chốt ca tổng hợp tiền mặt, điện tử, miễn/điều chỉnh, chênh lệch; nhân viên bàn giao, Ban quản lý xác nhận.

## Data Involved

`parking_sessions`, `gate_events`, `gate_event_media`, `media_assets`, `manual_reviews`, `barrier_actions`, `ai_inference_results`, `pricing_versions`, `parking_subscriptions`, `charges`, `charge_items`, `payments`, `work_shifts`, `alerts`, `audit_logs`, `users`.

## State Changes

Shift có `OPEN`/`CLOSED`; ERD ghi `confirmed_by_user_id` và `confirmed_at`. Alert có `OPEN`/`ACKNOWLEDGED`/`RESOLVED`. Chi tiết khi nào đóng ca và các điều kiện xác nhận beyond việc chốt ca cuối ca: **Not specified by approved sources.**

## AI Involvement

Chỉ tiêu AI gồm tỷ lệ nhận dạng biển và chất lượng ảnh mặt, cùng REVIEW/REJECT và nguyên nhân. Báo cáo thể hiện kết quả inference/decision; không suy ra đánh giá độ chính xác ngoài các chỉ tiêu đã nêu.

## Security / Authorization

Ban quản lý có màn hình quản lý/báo cáo; nhân viên trạm gác chỉ xem dữ liệu cần cho ca. Dữ liệu nhạy cảm trong ảnh, hồ sơ và audit phải kiểm soát quyền.

## Audit Requirements

Lịch sử cần hiển thị người duyệt. Hành động xem/sửa/xóa hồ sơ, manual barrier, thay đổi giá và điều chỉnh Payment phải có audit log. Báo cáo liệt kê điều chỉnh và thao tác thủ công.

## Acceptance Scenarios

- Tra cứu được ảnh vào-ra, phí và reviewer/operator liên quan.
- Doanh thu phân tích đúng theo ngày/tháng/ca, loại xe, loại khách, phương thức thanh toán.
- Báo cáo ca thể hiện cash/electronic, miễn/điều chỉnh và chênh lệch; có nhân viên bàn giao và manager xác nhận.
- Dashboard/báo cáo thể hiện sessions đang mở, thời gian gửi trung bình, >24h, AI recognition/image-quality và REVIEW/REJECT theo nguồn §4.2.

## Not Specified by Approved Sources

- Công thức chính xác của KPI/tỷ lệ và quy tắc kỳ ghi nhận doanh thu khi có refund/adjustment/offline.
- Filter/sort/export formats, lịch refresh dashboard và retention của báo cáo.
- Cách tính và quy trình giải quyết chênh lệch tiền sau khi quản lý xác nhận.
