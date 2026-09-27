# NV01 - Đăng ký căn hộ, cư dân và phương tiện

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md` — Yêu cầu 1 / NV01; `Ket_Qua_Khao_Sat_Bai_Xe.md` — §4.4, §5; `ERD.pdf` — bảng resident/apartment/vehicle/media/verification  
Implementation status: Not implemented.

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

ERD định nghĩa trạng thái căn hộ, cư dân, quan hệ membership/vehicle relation và `verification_result`. Nguồn nêu kết quả xác minh và quyền có hiệu lực. Chuyển trạng thái chi tiết giữa các enum trạng thái hồ sơ không được quy định đầy đủ: **Not specified by approved sources.**

## AI Involvement

AI/face matching tạo scores/kết quả cho ba so khớp; NV01 cần ghi nhận kết quả xác minh, model version và ngưỡng được dùng theo ERD. AI không tự tạo quyền thành viên hoặc quan hệ ủy quyền xe.

## Security / Authorization

- RBAC phải phân biệt Ban quản lý và nhân viên trạm gác; nguyên tắc quyền tối thiểu.
- Nhân viên trạm gác chỉ được xem dữ liệu cần cho ca; Ban quản lý mới được sửa hồ sơ theo khảo sát.
- CCCD, ảnh mặt và face template là dữ liệu nhạy cảm: mã hóa khi lưu/truyền, kiểm soát vùng ảnh; ưu tiên template thay vì dùng ảnh thô cho mọi phép so sánh.

## Audit Requirements

Mọi lần xem/sửa/xóa hồ sơ phải có audit log. Thao tác thủ công liên quan cũng phải truy ra tài khoản, thời gian, lý do và ảnh bằng chứng khi áp dụng.

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
