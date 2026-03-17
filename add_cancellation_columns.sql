-- Script SQL để thêm các cột hủy đặt phòng vào bảng bookings
-- Script này có thể chạy nhiều lần mà không bị lỗi
-- Chạy trên SQL Server database của bạn

-- Thêm cột cancellation_reason (Lý do hủy)
IF NOT EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID(N'[dbo].[bookings]') 
    AND name = 'cancellation_reason'
)
BEGIN
    ALTER TABLE bookings
    ADD cancellation_reason NVARCHAR(255) NULL;
    PRINT 'Đã thêm cột cancellation_reason';
END
ELSE
BEGIN
    PRINT 'Cột cancellation_reason đã tồn tại - bỏ qua';
END
GO

-- Thêm cột cancellation_note (Ghi chú hủy)
IF NOT EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID(N'[dbo].[bookings]') 
    AND name = 'cancellation_note'
)
BEGIN
    ALTER TABLE bookings
    ADD cancellation_note NVARCHAR(MAX) NULL;
    PRINT 'Đã thêm cột cancellation_note';
END
ELSE
BEGIN
    PRINT 'Cột cancellation_note đã tồn tại - bỏ qua';
END
GO

-- Thêm cột cancelled_at (Thời gian hủy)
IF NOT EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID(N'[dbo].[bookings]') 
    AND name = 'cancelled_at'
)
BEGIN
    ALTER TABLE bookings
    ADD cancelled_at DATETIME NULL;
    PRINT 'Đã thêm cột cancelled_at';
END
ELSE
BEGIN
    PRINT 'Cột cancelled_at đã tồn tại - bỏ qua';
END
GO

PRINT 'Hoàn thành! Kiểm tra các cột hủy đặt phòng trong bảng bookings.';
