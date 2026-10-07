# Smart Parking Documentation

Status: Approved specification materialization  
Source: `CNTT_KLCN101_Tran Van Tho.md`, `Ket_Qua_Khao_Sat_Bai_Xe.md`, `ERD.pdf`  
Implementation status: Backend authentication/RBAC, the server-rendered MANAGEMENT Web foundation, and the AHR-03 Apartment, AHR-04 Resident profile, AHR-05 Household Membership, AHR-06 household-head/Apartment lifecycle, AHR-07 Vehicle lookup/OWNER, AHR-08 AUTHORIZED_USER grant/query, AHR-09 VehicleRight lifecycle/guarantor-loss integration, AHR-10 Resident status lifecycle, and AHR-11 Membership/VehicleRight VOID subsets are implemented as documented in [Authentication and RBAC](security/auth-rbac.md) and [Backend Architecture](architecture/backend-architecture.md); the remaining approved NV01–NV08 business workflows are not implemented.

## Purpose

`docs/` là bản Markdown hướng triển khai, được tổ chức để con người và coding agents tra cứu yêu cầu dự án đã duyệt. Các tài liệu này hỗ trợ triển khai và không thay thế hay định nghĩa lại các nguồn được duyệt.

## Source of Truth

Primary approved references:

1. `CNTT_KLCN101_Tran Van Tho.md`
2. `Ket_Qua_Khao_Sat_Bai_Xe.md`
3. `ERD.pdf`

Các Markdown trong repository materialize những nguồn trên; khi chi tiết không được nguồn xác định, tài liệu ghi **“Not specified by approved sources.”** Markdown không tự bổ sung nghiệp vụ hoặc thay đổi ERD.

## Documentation Map

- [Tổng quan dự án](project/overview.md)
- [Kiến trúc backend](architecture/backend-architecture.md)
- [Backend authentication and RBAC](security/auth-rbac.md)
- [Tổng quan nghiệp vụ NV01–NV08](business/business-overview.md)
- Chi tiết nghiệp vụ:
  - [NV01 — Đăng ký căn hộ, cư dân và phương tiện](business/nv01-resident-vehicle-registration.md)
  - [NV02 — Thẻ, gói gửi xe và thanh toán](business/nv02-card-subscription-payment.md)
  - [NV03 — Cư dân vào bãi](business/nv03-resident-entry.md)
  - [NV04 — Cư dân ra bãi](business/nv04-resident-exit.md)
  - [NV05 — Khách vãng lai](business/nv05-visitor-parking.md)
  - [NV06 — Phân loại phương tiện bằng AI](business/nv06-ai-vehicle-classification.md)
  - [NV07 — Sự cố và xử lý thủ công](business/nv07-incident-manual-processing.md)
  - [NV08 — Tra cứu, báo cáo và đối soát](business/nv08-reporting-reconciliation.md)
- Database:
  - [Tổng quan database và Code First](database/database-overview.md)
  - [Data dictionary](database/data-dictionary.md)
  - [Relationships](database/relationships.md)
  - [Enums và trạng thái](database/enums.md)

## Rules for Coding Agents

1. Đọc tài liệu nghiệp vụ liên quan trước khi sửa một feature.
2. Đọc tài liệu database liên quan trước khi sửa persistence.
3. Không thay đổi business rule đã tài liệu hóa một cách âm thầm.
4. Không thiết kế lại hoặc loại bỏ concept trong ERD đã duyệt nếu chưa có phê duyệt của nhóm.
5. API contract được tài liệu hóa khi API tương ứng được thiết kế; không tự tạo endpoint từ tài liệu nghiệp vụ.
6. Nếu tài liệu và implementation mâu thuẫn, báo cáo mâu thuẫn và chờ quyết định thay vì âm thầm chọn một phía.
7. “Not specified by approved sources” không phải là quyền tự phát minh hành vi.
