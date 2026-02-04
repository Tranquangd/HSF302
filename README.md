account:<img width="474" height="146" alt="image" src="https://github.com/user-attachments/assets/fa8f06f1-a687-45e6-b568-1c0d5211d6e6" />


LUỒNG 1: Đặt hàng và thanh toáṇ̣̣̣( đã hoàn thành, cần sửa lại UI hiển thị trang home)

Actor: Người dùng
Mục tiêu: Đặt phòng/dịch vụ và thanh toán thành công

Flow:

Người dùng đăng nhập / đăng ký

Tìm kiếm phòng (theo ngày, loại phòng, giá)

Xem chi tiết phòng

Chọn phòng → nhấn Đặt hàng

Nhập thông tin khách hàng

Chọn phương thức thanh toán (VNPay / Momo / thẻ / tiền mặt)

Hệ thống xử lý thanh toán

Thanh toán thành công

Hệ thống:

Tạo đơn đặt phòng

Cập nhật trạng thái PAID

Gửi email / thông báo xác nhận

📌 Output: Đơn hàng hợp lệ + hóa đơn

🔹 LUỒNG 2: Hủy đặt phòng( chưa hoàn thành)

Actor: Người dùng
Mục tiêu: Hủy đơn đã đặt theo chính sách

Flow:

Người dùng đăng nhập

Vào Lịch sử đặt phòng

Chọn đơn đặt phòng

Nhấn Hủy đặt phòng

Hệ thống kiểm tra:

Thời gian hủy

Chính sách hoàn tiền

Người dùng xác nhận hủy

Hệ thống:

Cập nhật trạng thái CANCELLED

Hoàn tiền (nếu có)

Gửi thông báo cho người dùng

📌 Output: Đơn bị hủy + cập nhật tài chính

🔹 LUỒNG 3: Báo cáo – thống kê doanh thu( chưa hoàn thành)

Actor: Admin / Manager
Mục tiêu: Theo dõi hiệu quả kinh doanh

Flow:

Admin đăng nhập hệ thống

Truy cập Dashboard

Chọn khoảng thời gian (ngày / tháng / năm)

Hệ thống tổng hợp dữ liệu:

Tổng số đơn

Doanh thu

Đơn bị hủy

Doanh thu theo phòng / dịch vụ

Hiển thị:

Biểu đồ

Bảng thống kê

(Optional) Xuất file Excel / PDF

📌 Output: Báo cáo doanh thu & thống kê

🔹 LUỒNG 4: Đánh giá & kiểm soát hành vi người dùng (AI)( chưa hàon thành)

Actor: Người dùng + Hệ thống AI + Admin
Mục tiêu: Thu thập đánh giá & hạn chế hành vi xấu

Flow:

Người dùng hoàn thành đơn đặt phòng

Hệ thống cho phép đánh giá

Người dùng:

Chấm sao

Viết bình luận

AI xử lý nội dung đánh giá:

Phát hiện spam

Phát hiện ngôn từ tiêu cực / xúc phạm

Phân tích cảm xúc

Hệ thống:

Tự động duyệt / ẩn đánh giá

Gắn cờ người dùng có hành vi xấu

Admin:

Xem danh sách đánh giá

Khóa / cảnh cáo người dùng (nếu cần)

📌 Output: Đánh giá hợp lệ + hệ thống an toàn hơn
