# Hotel Booking System - Test Demo Guide

## 1. Prerequisites

- **Java 21** installed
- **SQL Server** running on `localhost:1433` with database `HotelManager`
- SQL Server credentials: `sa` / `12345`
- Application runs on **http://localhost:8088**

## 2. Start the Application

```bash
./mvnw spring-boot:run
```

Or in Windows:
```cmd
mvnw.cmd spring-boot:run
```

## 3. Default Test Accounts

The system uses the `[Login]` table. If using `DataInitializer`, these accounts may be pre-created:

| User ID   | Password | Role     | Description          |
|-----------|----------|----------|----------------------|
| admin     | admin    | admin    | Full access          |
| manager1  | 123456   | manager  | Manage bookings/rooms|
| staff1    | 123456   | staff    | View bookings only   |
| customer1 | 123456   | customer | Book rooms           |

> Check your `DataInitializer.java` or database for actual credentials.

---

## 4. Test Scenarios

### 4.1 Register Account (Dang ky tai khoan)

1. Go to **http://localhost:8088**
2. Click **Register** (or go to **http://localhost:8088/register**)
3. Fill in:
   - **User ID**: `testuser1`
   - **Full Name**: `Test User`
   - **Password**: `1234`
   - **Confirm Password**: `1234`
4. Click **Register**
5. You should see success message and be redirected to Login page
6. Login with `testuser1` / `1234`

### 4.2 Search & Book a Room

1. Login as a customer (e.g., `testuser1`)
2. Click **Search Available Rooms**
3. Fill in:
   - **Check-in Date**: tomorrow's date
   - **Check-out Date**: day after tomorrow
   - **Number of Guests**: 2
   - **Room Type**: (leave empty for all, or select specific type)
4. Click **Search**
5. Select a room and click **Book Now**
6. Fill in customer information (name, email, phone)
7. Click **Create Booking**
8. Note the **Booking ID** shown

### 4.3 Payment with Cash/Card

1. After creating a booking, click **Proceed to Payment**
2. Select payment method:
   - **Cash**: Instant confirmation (pay at reception)
   - **Card**: Fill in any test card info, instant confirmation
3. Click **Pay Now**
4. Booking status changes to **CONFIRMED**

### 4.5 Cancel Booking (Huy dat phong)

#### As Customer:
1. Login as customer
2. Go to **My Bookings** (http://localhost:8088/bookings/my-bookings)
3. Find a booking with status **PENDING_PAYMENT** or **CONFIRMED**
4. Click **Cancel** button
5. Confirm the cancellation
6. Booking status changes to **CANCELLED**

#### As Admin/Manager:
1. Login as admin or manager
2. Go to **Admin Panel** -> **Bookings**
3. Find the booking to cancel
4. Click **Cancel** button
5. Booking status changes to **CANCELLED**

### 4.6 Refund Payment (Hoan tien)

#### Method 1: Cancel & Refund (from Bookings)
1. Login as **admin** or **manager**
2. Go to **Admin Panel** -> **Bookings**
3. Find a **CONFIRMED** booking that has been paid
4. Click **Refund** button
5. This will cancel the booking AND refund the payment
6. Booking status -> **CANCELLED**, Payment status -> **REFUNDED**

Refund policy is based on cancellation time vs check-in time:

- Cancel before `refund.policy.full.hours` (default 24 hours) => **100%** refund
- Cancel within that window but before check-in => **partial** refund (default `refund.policy.partial.percentage` = 50%)
- Cancel at/after check-in time => **0%** refund

Each refund creates a separate **Refund** record and a **TransactionLog** entry for audit.

#### Method 2: Refund from Payment Management
1. Login as **admin**
2. Go to **Admin Panel** -> **Payments**
3. Find a payment with status **SUCCESS**
4. Click **Refund** button
5. Payment status changes to **REFUNDED**
6. Associated booking status changes to **CANCELLED**

#### Method 3: Refund from Booking Details (Admin)
1. Go to **Admin Panel** -> **Bookings** -> Click **View** on a booking
2. In the Quick Actions section:
   - **Cancel & Refund**: Cancels booking + refunds payment
   - **Refund Payment**: Only refunds (if booking already cancelled)

---

## 5. Admin Panel Access

| URL                              | Role Required        | Description           |
|----------------------------------|----------------------|-----------------------|
| /admin/dashboard                 | Admin, Manager, Staff| Dashboard overview    |
| /admin/bookings                  | Admin, Manager, Staff| Manage bookings       |
| /admin/rooms                     | Admin, Manager, Staff| Manage rooms          |
| /admin/customers                 | Admin, Manager       | Manage customers      |
| /admin/users                     | Admin only           | Manage users          |
| /admin/payments                  | Admin only           | Manage payments       |

---

## 6. API Endpoints (for Postman/REST clients)

Authentication required (JWT token in `Authorization: Bearer <token>` header):

### Auth
- `POST /api/auth/login` - Login (get JWT token)
- `POST /api/auth/register` - Register new account

### Bookings
- `POST /api/bookings/search-rooms` - Search available rooms (public)
- `POST /api/bookings/create` - Create booking
- `GET /api/bookings/{id}` - Get booking details
- `POST /api/bookings/{id}/cancel` - Cancel booking

### Payments
- `POST /api/payments/process` - Process payment
- `POST /api/payments/refund/{bookingId}` - Refund payment

---

## 7. Troubleshooting

- **Cannot connect to database**: Ensure SQL Server is running and `HotelManager` database exists
- **No rooms found**: Check that rooms are seeded in the database (see `DataInitializer.java`)
- **Login fails**: Check the `[Login]` table in database for valid credentials
- **403 Forbidden on admin pages**: Ensure you're logged in with the correct role (admin/manager/staff)
