#!/bin/bash
set -e

echo "=== 秒杀系统 ECS 部署脚本 ==="

# 1. 安装 Docker（如未安装）
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
fi

# 2. 构建前端
echo "构建前端..."
cd seckill-frontend
npm install
npm run build
cd ..

# 3. 启动全栈服务（2 实例集群）
echo "启动 Docker 服务..."
docker compose up -d --build --scale seckill-app=2

# 4. 等待服务就绪
echo "等待服务启动..."
sleep 30

# 5. 预热 Redis 库存
echo "预热库存..."
curl -s -X POST http://localhost:9000/api/admin/warmup

echo ""
echo "=== 部署完成 ==="
echo "前端: http://<ECS公网IP>"
echo "API网关: http://<ECS公网IP>:9000"
echo "RabbitMQ管理台: http://<ECS公网IP>:15672 (guest/guest)"
echo ""
echo "压测命令:"
echo "  jmeter -n -t deploy/jmeter/seckill-1000users-100stock.jmx -l result.jtl -e -o report/"
