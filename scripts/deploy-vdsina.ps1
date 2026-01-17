# Deploy script for VDSina VDS
# Usage: .\deploy-vdsina.ps1 -ServerIP <ip>

param(
    [Parameter(Mandatory=$true)]
    [string]$ServerIP
)

$ErrorActionPreference = "Stop"
$SSHKeyPath = "$env:USERPROFILE\.ssh\id_rsa"
$RemoteUser = "root"
$ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
$EnvFile = "$ProjectRoot\.env.production"

Write-Host "=== VDSina Deploy Script ===" -ForegroundColor Cyan
Write-Host "Server IP: $ServerIP"
Write-Host "SSH Key: $SSHKeyPath"
Write-Host "Project Root: $ProjectRoot"

# Validate prerequisites
if (-not (Test-Path $SSHKeyPath)) {
    throw "SSH key not found: $SSHKeyPath"
}
if (-not (Test-Path $EnvFile)) {
    throw ".env.production not found: $EnvFile"
}

# Step 1: Build application locally
Write-Host "`n[1/6] Building application..." -ForegroundColor Yellow
Push-Location $ProjectRoot
try {
    & .\gradlew.bat :server:installDist :composeApp:wasmJsBrowserDistribution --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
} finally {
    Pop-Location
}

# Step 2: Wait for SSH availability
Write-Host "`n[2/6] Waiting for SSH availability..." -ForegroundColor Yellow
$maxAttempts = 30
$attempt = 0
do {
    $attempt++
    Write-Host "Attempt $attempt/$maxAttempts..."
    $result = ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -i $SSHKeyPath "$RemoteUser@$ServerIP" "echo ok" 2>&1
    if ($result -eq "ok") { break }
    Start-Sleep -Seconds 10
} while ($attempt -lt $maxAttempts)
if ($attempt -ge $maxAttempts) { throw "SSH connection timeout" }
Write-Host "SSH connection established"

# Step 3: Install Docker on VDS
Write-Host "`n[3/6] Installing Docker on VDS..." -ForegroundColor Yellow
$installDockerScript = @"
apt-get update
apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu noble stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable docker
systemctl start docker
"@
ssh -i $SSHKeyPath "$RemoteUser@$ServerIP" $installDockerScript
if ($LASTEXITCODE -ne 0) { throw "Docker installation failed" }

# Step 4: Create directories and copy files
Write-Host "`n[4/6] Copying files to VDS..." -ForegroundColor Yellow
ssh -i $SSHKeyPath "$RemoteUser@$ServerIP" "mkdir -p /opt/aichallenge/{server,frontend,config}"

# Copy server distribution
scp -i $SSHKeyPath -r "$ProjectRoot\server\build\install\server\*" "$RemoteUser@$ServerIP`:/opt/aichallenge/server/"

# Copy frontend distribution
scp -i $SSHKeyPath -r "$ProjectRoot\composeApp\build\dist\wasmJs\productionExecutable\*" "$RemoteUser@$ServerIP`:/opt/aichallenge/frontend/"

# Copy deployment configs
scp -i $SSHKeyPath "$ProjectRoot\deploy\docker-compose.yml" "$RemoteUser@$ServerIP`:/opt/aichallenge/"
scp -i $SSHKeyPath "$ProjectRoot\deploy\nginx.conf" "$RemoteUser@$ServerIP`:/opt/aichallenge/config/"
scp -i $SSHKeyPath "$EnvFile" "$RemoteUser@$ServerIP`:/opt/aichallenge/.env"

# Step 5: Start services
Write-Host "`n[5/6] Starting services..." -ForegroundColor Yellow
ssh -i $SSHKeyPath "$RemoteUser@$ServerIP" "cd /opt/aichallenge && docker compose up -d"
if ($LASTEXITCODE -ne 0) { throw "Docker compose failed" }

# Step 6: Verify deployment
Write-Host "`n[6/6] Verifying deployment..." -ForegroundColor Yellow
Start-Sleep -Seconds 10
$healthCheck = Invoke-RestMethod -Uri "http://$ServerIP/api" -TimeoutSec 30 -ErrorAction SilentlyContinue
if ($healthCheck) {
    Write-Host "`n=== Deployment Successful ===" -ForegroundColor Green
    Write-Host "Frontend: http://$ServerIP"
    Write-Host "API: http://$ServerIP/api"
} else {
    Write-Host "Warning: Health check failed, but services may still be starting" -ForegroundColor Yellow
}
