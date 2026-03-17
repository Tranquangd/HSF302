IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'HotelManager')
BEGIN
    CREATE DATABASE HotelManager;
END
GO

USE HotelManager;
GO

IF OBJECT_ID('payments', 'U') IS NOT NULL DROP TABLE payments;
IF OBJECT_ID('bookings', 'U') IS NOT NULL DROP TABLE bookings;
IF OBJECT_ID('customers', 'U') IS NOT NULL DROP TABLE customers;
IF OBJECT_ID('rooms', 'U') IS NOT NULL DROP TABLE rooms;
IF OBJECT_ID('[Login]', 'U') IS NOT NULL DROP TABLE [Login];
GO

CREATE TABLE [Login] (
    userid VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    username NVARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'manager', 'staff', 'customer')),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'suspended')),
    created_at DATETIME DEFAULT GETDATE()
);
GO

CREATE TABLE rooms (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    room_number VARCHAR(20) NOT NULL UNIQUE,
    room_type NVARCHAR(50) NOT NULL,
    price_per_night DECIMAL(10,2) NOT NULL CHECK (price_per_night > 0),
    capacity INT NOT NULL CHECK (capacity > 0),
    description NVARCHAR(1000),
    available BIT NOT NULL DEFAULT 1,
    image_url VARCHAR(500)
);
GO

CREATE TABLE customers (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    full_name NVARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL,
    created_at DATETIME DEFAULT GETDATE()
);
GO

CREATE TABLE bookings (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    room_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    number_of_guests INT NOT NULL CHECK (number_of_guests > 0),
    total_amount DECIMAL(12,2) NOT NULL CHECK (total_amount >= 0),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT' 
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED')),
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_bookings_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT FK_bookings_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT CHK_booking_dates CHECK (check_out_date > check_in_date)
);
GO

CREATE TABLE payments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL CHECK (amount >= 0),
    payment_method VARCHAR(30) NOT NULL 
        CHECK (payment_method IN ('CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'E_WALLET', 'CASH', 'VNPAY')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    transaction_id VARCHAR(100),
    payment_date DATETIME,
    failure_reason NVARCHAR(500),
    created_at DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
);
GO

CREATE INDEX IX_bookings_room_id ON bookings(room_id);
CREATE INDEX IX_bookings_customer_id ON bookings(customer_id);
CREATE INDEX IX_bookings_status ON bookings(status);
CREATE INDEX IX_bookings_dates ON bookings(check_in_date, check_out_date);
CREATE INDEX IX_payments_booking_id ON payments(booking_id);
CREATE INDEX IX_payments_status ON payments(status);
CREATE INDEX IX_rooms_available ON rooms(available);
CREATE INDEX IX_rooms_room_type ON rooms(room_type);
CREATE INDEX IX_customers_email ON customers(email);
CREATE INDEX IX_login_role ON [Login](role);
GO

INSERT INTO [Login] (userid, password, username, role, status) VALUES
('admin', '123456', N'Administrator', 'admin', 'active'),
('manager1', '123456', N'Nguyễn Văn Manager', 'manager', 'active'),
('staff1', '123456', N'Trần Thị Staff', 'staff', 'active'),
('staff2', '123456', N'Lê Văn Staff', 'staff', 'active'),
('customer1', '123456', N'Phạm Minh Customer', 'customer', 'active'),
('customer2', '123456', N'Hoàng Thị Customer', 'customer', 'active');
GO

INSERT INTO rooms (room_number, room_type, price_per_night, capacity, description, available, image_url) VALUES
('101', N'Standard', 500000.00, 2, N'Phòng tiêu chuẩn với 1 giường đôi, view thành phố', 1, '/images/rooms/standard.jpg'),
('102', N'Standard', 500000.00, 2, N'Phòng tiêu chuẩn với 2 giường đơn, view thành phố', 1, '/images/rooms/standard.jpg'),
('103', N'Standard', 500000.00, 2, N'Phòng tiêu chuẩn với 1 giường đôi, view vườn', 1, '/images/rooms/standard.jpg'),
('201', N'Deluxe', 800000.00, 3, N'Phòng Deluxe rộng rãi với 1 giường king size, ban công riêng', 1, '/images/rooms/deluxe.jpg'),
('202', N'Deluxe', 800000.00, 3, N'Phòng Deluxe với 1 giường king size và 1 sofa bed', 1, '/images/rooms/deluxe.jpg'),
('203', N'Deluxe', 850000.00, 4, N'Phòng Deluxe Family với 2 giường đôi', 1, '/images/rooms/deluxe.jpg'),
('301', N'Suite', 1500000.00, 4, N'Suite cao cấp với phòng khách riêng, view panorama', 1, '/images/rooms/suite.jpg'),
('302', N'Suite', 1500000.00, 4, N'Suite cao cấp với bồn tắm jacuzzi, view biển', 1, '/images/rooms/suite.jpg'),
('401', N'Presidential', 3000000.00, 6, N'Phòng Tổng thống - 2 phòng ngủ, phòng khách, bếp mini', 1, '/images/rooms/presidential.jpg'),
('501', N'Penthouse', 5000000.00, 8, N'Penthouse sang trọng - Toàn bộ tầng 5, dịch vụ butler 24/7', 1, '/images/rooms/penthouse.jpg');
GO

INSERT INTO customers (full_name, email, phone_number) VALUES
(N'Nguyễn Văn An', 'nguyenvanan@gmail.com', '0901234567'),
(N'Trần Thị Bình', 'tranthibinh@gmail.com', '0912345678'),
(N'Lê Hoàng Cường', 'lehoangcuong@gmail.com', '0923456789'),
(N'Phạm Minh Đức', 'phamminhduc@gmail.com', '0934567890'),
(N'Hoàng Thị Lan', 'hoangthilan@gmail.com', '0945678901');
GO

INSERT INTO bookings (room_id, customer_id, check_in_date, check_out_date, number_of_guests, total_amount, status) VALUES
(1, 1, '2026-02-15', '2026-02-18', 2, 1500000.00, 'CONFIRMED'),
(4, 2, '2026-02-20', '2026-02-23', 3, 2400000.00, 'CONFIRMED'),
(7, 3, '2026-02-25', '2026-02-28', 4, 4500000.00, 'PENDING_PAYMENT'),
(2, 4, '2026-03-01', '2026-03-03', 2, 1000000.00, 'PENDING_PAYMENT'),
(9, 5, '2026-03-10', '2026-03-15', 5, 15000000.00, 'CONFIRMED');
GO

INSERT INTO payments (booking_id, amount, payment_method, status, transaction_id, payment_date) VALUES
(1, 1500000.00, 'VNPAY', 'SUCCESS', 'TXN-VNPAY001', '2026-02-14 10:30:00'),
(2, 2400000.00, 'CREDIT_CARD', 'SUCCESS', 'TXN-CC002', '2026-02-19 14:45:00'),
(5, 15000000.00, 'BANK_TRANSFER', 'SUCCESS', 'TXN-BT003', '2026-03-08 09:15:00');
GO

CREATE OR ALTER PROCEDURE sp_GetAvailableRooms
    @CheckInDate DATE,
    @CheckOutDate DATE,
    @NumberOfGuests INT = 1,
    @RoomType NVARCHAR(50) = NULL
AS
BEGIN
    SELECT r.*
    FROM rooms r
    WHERE r.available = 1
      AND r.capacity >= @NumberOfGuests
      AND (@RoomType IS NULL OR @RoomType = '' OR r.room_type = @RoomType)
      AND r.id NOT IN (
          SELECT b.room_id 
          FROM bookings b
          WHERE b.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
            AND b.check_in_date <= @CheckOutDate 
            AND b.check_out_date >= @CheckInDate
      )
    ORDER BY r.price_per_night;
END
GO

CREATE OR ALTER PROCEDURE sp_GetBookingStatistics
    @StartDate DATE = NULL,
    @EndDate DATE = NULL
AS
BEGIN
    SET @StartDate = ISNULL(@StartDate, DATEADD(MONTH, -1, GETDATE()));
    SET @EndDate = ISNULL(@EndDate, GETDATE());

    SELECT 
        status,
        COUNT(*) as total_bookings,
        SUM(total_amount) as total_revenue
    FROM bookings
    WHERE created_at BETWEEN @StartDate AND @EndDate
    GROUP BY status;

    SELECT 
        r.room_type,
        COUNT(b.id) as total_bookings,
        SUM(b.total_amount) as total_revenue
    FROM bookings b
    JOIN rooms r ON b.room_id = r.id
    WHERE b.status = 'CONFIRMED' OR b.status = 'CHECKED_OUT'
      AND b.created_at BETWEEN @StartDate AND @EndDate
    GROUP BY r.room_type;

    SELECT 
        payment_method,
        COUNT(*) as total_payments,
        SUM(amount) as total_amount
    FROM payments
    WHERE status = 'SUCCESS'
      AND payment_date BETWEEN @StartDate AND @EndDate
    GROUP BY payment_method;
END
GO

CREATE OR ALTER VIEW vw_ActiveBookings AS
SELECT 
    b.id as booking_id,
    r.room_number,
    r.room_type,
    c.full_name as customer_name,
    c.email as customer_email,
    c.phone_number as customer_phone,
    b.check_in_date,
    b.check_out_date,
    b.number_of_guests,
    b.total_amount,
    b.status,
    b.created_at
FROM bookings b
JOIN rooms r ON b.room_id = r.id
JOIN customers c ON b.customer_id = c.id
WHERE b.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN');
GO

CREATE OR ALTER VIEW vw_RoomAvailabilitySummary AS
SELECT 
    room_type,
    COUNT(*) as total_rooms,
    SUM(CASE WHEN available = 1 THEN 1 ELSE 0 END) as available_rooms,
    SUM(CASE WHEN available = 0 THEN 1 ELSE 0 END) as unavailable_rooms,
    MIN(price_per_night) as min_price,
    MAX(price_per_night) as max_price,
    AVG(price_per_night) as avg_price
FROM rooms
GROUP BY room_type;
GO

CREATE OR ALTER VIEW vw_PaymentSummary AS
SELECT 
    p.id as payment_id,
    b.id as booking_id,
    r.room_number,
    c.full_name as customer_name,
    p.amount,
    p.payment_method,
    p.status,
    p.transaction_id,
    p.payment_date,
    p.failure_reason
FROM payments p
JOIN bookings b ON p.booking_id = b.id
JOIN rooms r ON b.room_id = r.id
JOIN customers c ON b.customer_id = c.id;
GO

CREATE OR ALTER TRIGGER trg_UpdateBookingTimestamp
ON bookings
AFTER UPDATE
AS
BEGIN
    UPDATE bookings
    SET updated_at = GETDATE()
    WHERE id IN (SELECT id FROM inserted);
END
GO


IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'status')
BEGIN
    ALTER TABLE customers ADD status NVARCHAR(20) NOT NULL DEFAULT 'active';
END
GO


UPDATE customers SET status = 'active' WHERE status IS NULL;
GO

ALTER TABLE customers ADD user_id VARCHAR(255) NULL;

CREATE INDEX idx_customers_user_id ON customers(user_id);
