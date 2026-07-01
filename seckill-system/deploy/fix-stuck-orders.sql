-- 开发环境：查看卡住的处理中订单
SELECT o.id, u.username, o.order_no, o.product_id, o.status, o.create_time
FROM seckill_order o
JOIN user u ON u.id = o.user_id
WHERE o.status = 0
ORDER BY o.create_time;

-- 将超过 2 分钟仍为「处理中」的订单标记为失败（MQ 已修复后，历史脏数据）
UPDATE seckill_order
SET status = 2
WHERE status = 0
  AND create_time < NOW() - INTERVAL 2 MINUTE;

-- 修复商品名乱码（JDBC 改为 utf8mb4 后执行一次）
UPDATE product SET name = 'iPhone 16 Pro 秒杀' WHERE id = 1;

-- 重置 Redis 库存（需后端运行中）
-- Invoke-RestMethod -Method POST -Uri "http://localhost:9000/api/admin/warmup"
