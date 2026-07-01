# Local dev middleware starter (Windows PowerShell)
# Usage: .\deploy\start-dev.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $ProjectRoot

Write-Host "=== Seckill System - Local Dev ===" -ForegroundColor Cyan

try {
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "docker not available" }
} catch {
    Write-Host "[ERROR] Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

Write-Host "Starting MySQL / Redis / RabbitMQ ..." -ForegroundColor Yellow
docker compose up -d mysql redis rabbitmq
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] docker compose failed" -ForegroundColor Red
    exit 1
}

Write-Host "Waiting for MySQL healthy ..." -ForegroundColor Yellow
$maxWait = 60
$elapsed = 0
$mysqlReady = $false
while ($elapsed -lt $maxWait) {
    $status = docker inspect --format="{{.State.Health.Status}}" seckill-mysql 2>$null
    if ($status -eq "healthy") {
        $mysqlReady = $true
        break
    }
    Start-Sleep -Seconds 3
    $elapsed += 3
    Write-Host "  waited ${elapsed}s ..."
}

if (-not $mysqlReady) {
    Write-Host "[WARN] MySQL not healthy yet. Run: docker compose ps" -ForegroundColor Yellow
} else {
    Write-Host "[OK] MySQL healthy" -ForegroundColor Green
}

Write-Host "Checking Redis ..." -ForegroundColor Yellow
$redisPing = docker exec seckill-redis redis-cli ping 2>$null
if ($redisPing -eq "PONG") {
    Write-Host "[OK] Redis PONG" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Redis not responding. Backend will fail (Redisson connection refused)." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Middleware ready. Open 3 new terminals: ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Terminal 1 - Backend (8080):" -ForegroundColor White
Write-Host ('  Set-Location "' + $ProjectRoot + '\seckill-backend"')
Write-Host "  mvn spring-boot:run"
Write-Host ""
Write-Host "Terminal 2 - Gateway (9000):" -ForegroundColor White
Write-Host ('  Set-Location "' + $ProjectRoot + '\seckill-gateway"')
Write-Host "  mvn spring-boot:run"
Write-Host ""
Write-Host "Terminal 3 - Frontend (5173):" -ForegroundColor White
Write-Host ('  Set-Location "' + $ProjectRoot + '\seckill-frontend"')
Write-Host "  npm run dev"
Write-Host ""
Write-Host "Visit: http://localhost:5173" -ForegroundColor Green
Write-Host ""
Write-Host "Optional warmup:" -ForegroundColor Yellow
Write-Host '  Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/admin/warmup"'
