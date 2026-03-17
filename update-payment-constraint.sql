-- Script SQL để cập nhật CHECK constraint cho payment_method để thêm PAYPAL
-- Chạy script này trong SQL Server Management Studio với database HotelManager

USE [HotelManager];
GO

-- Bước 1: Xoá CHECK constraint cũ
IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'CK__payments__paymen__5441852A'
)
BEGIN
    ALTER TABLE dbo.payments
    DROP CONSTRAINT CK__payments__paymen__5441852A;
    PRINT 'Đã xoá constraint cũ';
END
ELSE
BEGIN
    PRINT 'Không tìm thấy constraint cũ, có thể tên khác';
END
GO

-- Bước 2: Tìm tên constraint thực tế nếu tên trên không đúng
-- (Chạy query này để xem tên constraint thực tế)
SELECT 
    cc.name AS ConstraintName,
    cc.definition AS ConstraintDefinition
FROM sys.check_constraints cc
INNER JOIN sys.tables t ON cc.parent_object_id = t.object_id
WHERE t.name = 'payments' AND t.schema_id = SCHEMA_ID('dbo');
GO

-- Bước 3: Tạo lại CHECK constraint với PAYPAL
ALTER TABLE dbo.payments
ADD CONSTRAINT CK__payments__paymen__5441852A
CHECK (payment_method IN (
    'CREDIT_CARD',
    'DEBIT_CARD',
    'BANK_TRANSFER',
    'E_WALLET',
    'CASH',
    'VNPAY',
    'PAYPAL'
));
GO

PRINT 'Đã tạo lại constraint với PAYPAL';
GO

-- Bước 4: Kiểm tra constraint đã được tạo đúng chưa
SELECT 
    cc.name AS ConstraintName,
    cc.definition AS ConstraintDefinition
FROM sys.check_constraints cc
INNER JOIN sys.tables t ON cc.parent_object_id = t.object_id
WHERE t.name = 'payments' AND t.schema_id = SCHEMA_ID('dbo');
GO
