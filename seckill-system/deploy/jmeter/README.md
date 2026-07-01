# JMeter 压测指南

## 安装 JMeter

| 平台 | 说明 |
|------|------|
| Windows | 下载 [apache-jmeter-5.6.3.zip](https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-5.6.3.zip)，解压后用 `bin\jmeter.bat`（未加入 PATH 时用完整路径） |
| macOS | `brew install jmeter`，或从官网下载 |
| Linux | 下载 tar.gz 解压，或使用包管理器 |

要求 **JMeter 5.x**，并已安装 **Java 8+**。

```bash
jmeter -v
```

## 测试场景

| 脚本 | 并发 | 说明 |
|------|------|------|
| [seckill-50users-100stock.jmx](seckill-50users-100stock.jmx) | 50（45 恶意 + 5 合法） | **本地 IDEA 轻量压测**，笔记本可跑 |
| [seckill-1000users-100stock.jmx](seckill-1000users-100stock.jmx) | 1000（950 恶意 + 50 合法） | **完整压测**，建议 Docker 多实例后端 |

- **商品库存**: 100（`schema.sql` 默认）
- **目标 URL**: `http://localhost:9000/api/seckill/1`

---

## 一、本地 IDEA 轻量压测（50 用户）

适合开发机 + IDEA Run 全栈（8080/9000/5173），无需 Docker 打包后端。

### 1. 启动服务

1. Docker 中间件：`docker compose up -d --build mysql redis rabbitmq`
2. IDEA **Run SeckillApplication**（网关 + 前端 + 后端）

### 2. 批量注册测试用户（生成 Token）

**Windows：**

```powershell
cd seckill-system
.\deploy\jmeter\prepare-users.ps1 -BaseUrl http://localhost:9000 -Count 50
```

**Linux / macOS：**

```bash
cd seckill-system
chmod +x deploy/jmeter/prepare-users.sh
./deploy/jmeter/prepare-users.sh http://localhost:9000 50
```

脚本会注册 `testuser001`…`testuser050`（密码 `test123456`），将 JWT 写入 [users.csv](users.csv)。

若全部返回 400（用户名已存在），可跳过并沿用已有 `users.csv`，或用登录刷新 Token：

```powershell
$tokens = @(); 1..50 | ForEach-Object { $u = "testuser{0:D3}" -f $_; $body = "{`"username`":`"$u`",`"password`":`"test123456`"}"; try { $r = Invoke-RestMethod -Method POST -Uri "http://localhost:9000/api/auth/login" -ContentType "application/json" -Body $body; if ($r.code -eq 200) { $tokens += $r.data.token } } catch {} }; "token" | Out-File deploy\jmeter\users.csv -Encoding utf8; $tokens | Add-Content deploy\jmeter\users.csv -Encoding utf8
```

> `users.csv` 含 Token，已加入 `.gitignore`，**勿提交到 Git**。模板见 [users.csv.example](users.csv.example)。

### 重测前重置（可选）

```powershell
docker exec seckill-mysql mysql -uroot -pseckill123 seckill -e "DELETE FROM seckill_order; DELETE FROM stock_log; UPDATE product SET stock=100, seckill_stock=100 WHERE id=1;"
docker exec seckill-redis redis-cli DEL seckill:stock:1 seckill:bought:1
Invoke-RestMethod -Method POST -Uri "http://localhost:9000/api/admin/warmup"
```

### 3. 预热库存

**Linux / macOS：**

```bash
curl -X POST http://localhost:9000/api/admin/warmup
```

**Windows PowerShell**（`curl -X` 不可用）：

```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:9000/api/admin/warmup"
```

### 4. 运行压测

在项目根 `seckill-system` 下执行。重跑前删除旧报告：`Remove-Item -Recurse -Force report, result.jtl -ErrorAction SilentlyContinue`

**Windows：**

```powershell
& "F:\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat" -n -t deploy/jmeter/seckill-50users-100stock.jmx -l result.jtl -e -o report/
```

**Linux / macOS：**

```bash
jmeter -n -t deploy/jmeter/seckill-50users-100stock.jmx -l result.jtl -e -o report/
```

> JMeter 报告 Error% 偏高属正常（45 个无 Token 请求计为 FAIL）。验收以 MySQL 成功订单数 ≤ 100 为准。

### 5. 查看报告

浏览器打开 `report/index.html`（已在 `.gitignore`，勿提交）。

### 6. 超卖校验

```sql
-- 成功订单应 <= 100
SELECT COUNT(*) FROM seckill_order WHERE status = 1;

-- 数据库库存
SELECT stock, seckill_stock FROM product WHERE id = 1;
```

```bash
docker exec seckill-redis redis-cli GET seckill:stock:1
```

---

## 二、Docker 全栈压测（1000 用户）

### 压测前检查

- [ ] 全栈已启动：`docker compose up -d --build --scale seckill-app=2`
- [ ] MySQL / Redis / RabbitMQ 均 healthy
- [ ] 前端已构建：`cd seckill-frontend && npm run build`
- [ ] 已运行 `prepare-users.ps1 -Count 50`（合法线程需 CSV Token）
- [ ] 库存已预热：`curl -X POST http://localhost:9000/api/admin/warmup`
- [ ] 网关可访问：`curl http://localhost:9000/api/products`

### 运行

```bash
jmeter -n -t deploy/jmeter/seckill-1000users-100stock.jmx -l result.jtl -e -o report/
```

### 测试环境参考

| 项目 | 配置 |
|------|------|
| CPU | 4 核 |
| 内存 | 8 GB |
| 后端实例 | 2（docker compose scale） |
| Redis | 7-alpine |
| MySQL | 8.0 |
| RabbitMQ | 3-management |

### 验收指标

| 指标 | 目标 | 实测 | 是否达标 |
|------|------|------|----------|
| 单机 QPS | ≥ 1200 | ___ | |
| 平均响应时间 | ≤ 180ms | ___ | |
| 超卖检测 | 0 | ___ | |
| 5xx 错误 | 0 | ___ | |
| 恶意拦截率 | ≥ 95% | ___ | |
| 成功订单数 | ≤ 100 | ___ | |

---

## 削峰验证

- RabbitMQ 管理台（http://localhost:15672，guest/guest）观察入队速率
- 消费者落库速率约 200 QPS（Redisson 限速）
- DB 负载较直连落库明显降低

---

## 常见问题

| 现象 | 处理 |
|------|------|
| `jmeter` 无法识别 | 使用 `jmeter.bat` 完整路径 |
| `prepare-users` 全部 400 | 用户已存在；跳过或登录刷新 Token |
| `report` 目录非空 | 删除 `report/`、`result.jtl` 后重跑 |
| 压测后订单数为 0 | 执行 warmup；确认 Redis 库存 key 存在 |
| 网页登录测试账号失败 | 选「登录」非「注册」；密码 `test123456` |

---

## 手工多账号演示（非压测）

3～5 个浏览器无痕窗口分别注册 → 秒杀，适合功能截图，**不能替代 JMeter 并发验收**。
