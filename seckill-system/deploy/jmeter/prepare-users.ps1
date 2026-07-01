# Batch register test users and write JWT tokens to users.csv for JMeter.
# Usage: .\deploy\jmeter\prepare-users.ps1 [-BaseUrl http://localhost:9000] [-Count 50]

param(
    [string]$BaseUrl = "http://localhost:9000",
    [int]$Count = 50,
    [string]$Password = "test123456"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$CsvPath = Join-Path $ScriptDir "users.csv"

Write-Host "=== Prepare JMeter users ===" -ForegroundColor Cyan
Write-Host "API: $BaseUrl/api/auth/register"
Write-Host "Count: $Count -> $CsvPath"

$tokens = New-Object System.Collections.Generic.List[string]
$failed = 0

for ($i = 1; $i -le $Count; $i++) {
    $username = "testuser{0:D3}" -f $i
    $body = @{ username = $username; password = $Password } | ConvertTo-Json -Compress
    try {
        $resp = Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/register" `
            -ContentType "application/json; charset=utf-8" -Body $body
        if ($resp.code -eq 200 -and $resp.data.token) {
            $tokens.Add($resp.data.token)
            Write-Host "  [OK] $username" -ForegroundColor Green
        } else {
            # User may exist; try login
            $loginBody = @{ username = $username; password = $Password } | ConvertTo-Json -Compress
            $loginResp = Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/login" `
                -ContentType "application/json; charset=utf-8" -Body $loginBody
            if ($loginResp.code -eq 200 -and $loginResp.data.token) {
                $tokens.Add($loginResp.data.token)
                Write-Host "  [OK] $username (login)" -ForegroundColor Yellow
            } else {
                $failed++
                Write-Host "  [FAIL] $username : $($resp.message)" -ForegroundColor Red
            }
        }
    } catch {
        $failed++
        Write-Host "  [FAIL] $username : $_" -ForegroundColor Red
    }
}

if ($tokens.Count -eq 0) {
    Write-Host "[ERROR] No tokens collected. Is the backend/gateway running?" -ForegroundColor Red
    exit 1
}

"token" | Out-File -FilePath $CsvPath -Encoding utf8
$tokens | ForEach-Object { $_ } | Add-Content -Path $CsvPath -Encoding utf8

Write-Host ""
Write-Host "[DONE] $($tokens.Count) tokens written to users.csv (failed: $failed)" -ForegroundColor Green
Write-Host "Next: curl -X POST $BaseUrl/api/admin/warmup" -ForegroundColor Yellow
