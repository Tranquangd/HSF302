-- SQL Server: Thêm ràng buộc liên kết Customer <-> Login (SAFE SCRIPT)
-- Mục tiêu:
-- 1) customers.user_id tham chiếu tới [Login].userid (FOREIGN KEY)
-- 2) (khuyến nghị) mỗi user_id chỉ map tới 1 customer (UNIQUE filtered index)
--
-- Script này sẽ:
-- - In ra dữ liệu lệch / dữ liệu trùng
-- - TỰ xử lý trùng user_id: giữ 1 bản ghi (mới nhất), các bản ghi còn lại set user_id = NULL
-- - TỰ đồng bộ kiểu dữ liệu customers.user_id theo đúng kiểu của [Login].userid
-- - Tạo UNIQUE index + FOREIGN KEY

PRINT '=== CHECK dữ liệu lệch: customers.user_id không tồn tại trong [Login].userid ===';
SELECT c.id, c.full_name, c.email, c.user_id
FROM customers c
LEFT JOIN [Login] l ON l.userid = c.user_id
WHERE c.user_id IS NOT NULL
  AND l.userid IS NULL;
GO

PRINT '=== CHECK trùng user_id trong customers (nếu muốn 1-1) ===';
SELECT c.user_id, COUNT(*) AS cnt
FROM customers c
WHERE c.user_id IS NOT NULL
GROUP BY c.user_id
HAVING COUNT(*) > 1;
GO

PRINT '=== FIX trùng user_id: giữ 1 dòng/customer mới nhất, còn lại set NULL ===';
;WITH d AS (
    SELECT
        c.id,
        c.user_id,
        ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY c.created_at DESC, c.id DESC) AS rn
    FROM customers c
    WHERE c.user_id IS NOT NULL
)
UPDATE c
SET c.user_id = NULL
FROM customers c
INNER JOIN d ON d.id = c.id
WHERE d.rn > 1;
PRINT 'Done fix duplicates (if any).';
GO

PRINT '=== FIX user_id không tồn tại trong [Login]: set NULL để tạo FK được ===';
UPDATE c
SET c.user_id = NULL
FROM customers c
LEFT JOIN [Login] l ON l.userid = c.user_id
WHERE c.user_id IS NOT NULL
  AND l.userid IS NULL;
PRINT 'Done fix invalid user_id (if any).';
GO

PRINT '=== SYNC data type: customers.user_id -> same as [Login].userid ===';
DECLARE @loginType SYSNAME, @loginMaxLen INT, @loginCollation SYSNAME;
DECLARE @ddl NVARCHAR(MAX);

SELECT
    @loginType = t.name,
    @loginMaxLen = c.max_length,
    @loginCollation = c.collation_name
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'[dbo].[Login]')
  AND c.name = 'userid';

IF @loginType IS NULL
BEGIN
    THROW 50001, 'Cannot find [dbo].[Login].userid column. Check table name/schema.', 1;
END

-- Build type string
DECLARE @typeStr NVARCHAR(200);
IF @loginMaxLen = -1
    SET @typeStr = QUOTENAME(@loginType) + N'(MAX)';
ELSE IF @loginType IN ('nvarchar','nchar')
    SET @typeStr = QUOTENAME(@loginType) + N'(' + CAST(@loginMaxLen/2 AS NVARCHAR(10)) + N')';
ELSE IF @loginType IN ('varchar','char')
    SET @typeStr = QUOTENAME(@loginType) + N'(' + CAST(@loginMaxLen AS NVARCHAR(10)) + N')';
ELSE
    SET @typeStr = QUOTENAME(@loginType); -- fallback (rare)

-- Drop index trước khi ALTER COLUMN (nếu đã tồn tại)
IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_customers_user_id_not_null'
      AND object_id = OBJECT_ID(N'[dbo].[customers]')
)
BEGIN
    PRINT 'Dropping index UX_customers_user_id_not_null (needed for ALTER COLUMN)...';
    DROP INDEX UX_customers_user_id_not_null ON [dbo].[customers];
END

-- Drop FK nếu đã tồn tại (để recreate sau)
IF EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_customers_login_userid'
      AND parent_object_id = OBJECT_ID(N'[dbo].[customers]')
)
BEGIN
    PRINT 'Dropping FK FK_customers_login_userid (will recreate)...';
    ALTER TABLE [dbo].[customers] DROP CONSTRAINT FK_customers_login_userid;
END

SET @ddl = N'ALTER TABLE [dbo].[customers] ALTER COLUMN [user_id] ' + @typeStr
    + CASE WHEN @loginCollation IS NOT NULL AND @loginType IN ('nvarchar','nchar','varchar','char')
           -- COLLATE không dùng dấu [] trong SQL Server
           THEN N' COLLATE ' + @loginCollation
           ELSE N''
      END
    + N' NULL;';

PRINT @ddl;
BEGIN TRY
    EXEC sp_executesql @ddl;
    PRINT 'Done sync data type.';
END TRY
BEGIN CATCH
    PRINT 'ERROR sync data type. Không thể tiếp tục tạo FK.';
    THROW;
END CATCH
GO

-- 1) Tạo UNIQUE filtered index để tránh 2 customers dùng chung 1 user_id (khuyến nghị)
IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_customers_user_id_not_null'
      AND object_id = OBJECT_ID(N'[dbo].[customers]')
)
BEGIN
    CREATE UNIQUE INDEX UX_customers_user_id_not_null
    ON [dbo].[customers]([user_id])
    WHERE [user_id] IS NOT NULL;
    PRINT 'Created UNIQUE filtered index UX_customers_user_id_not_null';
END
ELSE
BEGIN
    PRINT 'Index UX_customers_user_id_not_null already exists';
END
GO

-- 2) Thêm FOREIGN KEY customers.user_id -> [Login].userid
IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_customers_login_userid'
      AND parent_object_id = OBJECT_ID(N'[dbo].[customers]')
)
BEGIN
    ALTER TABLE [dbo].[customers]
    WITH CHECK
    ADD CONSTRAINT FK_customers_login_userid
    FOREIGN KEY ([user_id]) REFERENCES [dbo].[Login]([userid]);

    ALTER TABLE [dbo].[customers]
    CHECK CONSTRAINT FK_customers_login_userid;

    PRINT 'Created FK FK_customers_login_userid';
END
ELSE
BEGIN
    PRINT 'FK FK_customers_login_userid already exists';
END
GO

PRINT 'Done.';

