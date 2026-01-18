# Deploy script for VDSina VDS
# Usage: .\deploy-vdsina.ps1 -ServerIP <ip> -Password <password> [-HostKey <fingerprint>]
# Example: .\deploy-vdsina.ps1 -ServerIP 89.124.67.120 -Password "mypass" -HostKey "SHA256:3bGvl1boITk39QUDxVi00dryU5+KydZMFKBOtp44bSA"

param(
    [Parameter(Mandatory=$true)]
    [string]$ServerIP,

    [Parameter(Mandatory = $true)]
    [string]$Password,

    [Parameter(Mandatory = $false)]
    [string]$HostKey = ""
)

$ErrorActionPreference = "Stop"
$RemoteUser = "root"
$ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
$EnvFile = "$ProjectRoot\.env.production"

Write-Output "=== VDSina Deploy Script ==="
Write-Output "Server IP: $ServerIP"
Write-Output "Project Root: $ProjectRoot"

# Validate prerequisites
if (-not (Test-Path $EnvFile)) {
    throw ".env.production not found: $EnvFile"
}

# Check for plink
$plinkPath = "plink.exe"
if (-not (Get-Command $plinkPath -ErrorAction SilentlyContinue))
{
    throw "plink.exe not found. Please install PuTTY from https://www.putty.org/"
}

# Clean PuTTY registry cache
try
{
    $regPath = "HKCU:\Software\SimonTatham\PuTTY\SshHostKeys"
    if (Test-Path $regPath)
    {
        Get-Item $regPath | ForEach-Object {
            $_.Property | Where-Object { $_ -like "*$ServerIP*" } | ForEach-Object {
                Remove-ItemProperty -Path $regPath -Name $_ -ErrorAction SilentlyContinue
            }
        }
    }
}
catch
{
    Write-Output "  Note: Could not clean PuTTY registry cache"
}

# Add host key to known_hosts automatically
$knownHostsPath = "$env:USERPROFILE\.ssh\known_hosts"

# Ensure .ssh directory exists
$sshDir = "$env:USERPROFILE\.ssh"
if (-not (Test-Path $sshDir))
{
    New-Item -Path $sshDir -ItemType Directory -Force -ErrorAction SilentlyContinue
}

# Add host entry if not present
try
{
    $knownHostsContent = if (Test-Path $knownHostsPath)
    {
        Get-Content $knownHostsPath -Raw
    }
    else
    {
        ""
    }
    if (-not ($knownHostsContent -and $knownHostsContent.Contains($ServerIP)))
    {
        $hostEntry = "$ServerIP ssh-ed25519 AAAAC3lzaC1lZm1zc3MTYTAzMzYzMzYzMTU3MTUzYzMzYzMTU3MTUzYwAAAAB3tkbVXQHc6x+Hc+I9xv2UY1kPm+3wM9v+1x1VlZWxsTJ8fN4Jr8KfKj8w== root@$ServerIP"
        Add-Content -Path $knownHostsPath -Value "`n$hostEntry" -Force
        Write-Output "  Added $ServerIP to known_hosts"
    }
}
catch
{
    Write-Output "  Note: Could not update known_hosts"
}

# Step 1: Build application locally
Write-Output "`n[1/5] Building application..."
Push-Location $ProjectRoot
try {
    & .\gradlew.bat :server:installDist :mcp:vdsina:installDist :composeApp:wasmJsBrowserDistribution --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
} finally {
    Pop-Location
}

# Setup host key argument for plink/pscp
if ($HostKey)
{
    $hostKeyArg = "-hostkey `"$HostKey`""
    Write-Output "Using provided host key: $HostKey"
}
else
{
    # Get host key fingerprint from server
    Write-Output "`nGetting SSH host key fingerprint..."
    Write-Output "  If this hangs, run the script with -HostKey parameter:"
    Write-Output "  .\deploy-vdsina.ps1 -ServerIP $ServerIP -Password *** -HostKey `"SHA256:xxx`""

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $plinkPath
    $psi.Arguments = "-ssh -pw `"$Password`" $RemoteUser@$ServerIP exit"
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = [System.Diagnostics.Process]::Start($psi)
    $stderr = $process.StandardError.ReadToEndAsync()
    Start-Sleep -Milliseconds 2000
    $process.StandardInput.WriteLine("y")
    $process.StandardInput.Close()
    $process.WaitForExit(15000)
    $errorOutput = $stderr.Result

    if ($errorOutput -match "SHA256:([A-Za-z0-9+/=]+)")
    {
        $HostKey = "SHA256:$( $Matches[1] )"
        $hostKeyArg = "-hostkey `"$HostKey`""
        Write-Output "  Detected host key: $HostKey"
    }
    else
    {
        $hostKeyArg = ""
        Write-Output "  Warning: Could not detect host key, continuing without verification"
    }
}

# Helper function to run plink commands
function Invoke-Plink
{
    param([string]$Command)
    $argList = @("-ssh", "-pw", $Password, "-batch", "$RemoteUser@$ServerIP")
    if ($HostKey)
    {
        $argList = @("-ssh", "-pw", $Password, "-hostkey", $HostKey, "-batch", "$RemoteUser@$ServerIP")
    }
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $plinkPath @argList $Command 2>&1 | ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord])
    {
        Write-Host $_.ToString()
    }
    else
    {
        $_
    } }
    $script:LastPlinkExitCode = $LASTEXITCODE
    $ErrorActionPreference = $oldErrorAction
}

# Helper function to run pscp commands
function Invoke-Pscp
{
    param([string]$Source, [string]$Dest, [switch]$Recursive, [switch]$Compress)
    $argList = @("-scp", "-pw", $Password)
    if ($HostKey)
    {
        $argList += @("-hostkey", $HostKey)
    }
    if ($Recursive)
    {
        $argList += "-r"
    }
    if ($Compress)
    {
        $argList += "-C"
    }
    $argList += @($Source, "$RemoteUser@${ServerIP}:$Dest")
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & pscp @argList 2>&1 | ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord])
    {
        Write-Host $_.ToString()
    }
    else
    {
        $_
    } }
    $script:LastPscpExitCode = $LASTEXITCODE
    $ErrorActionPreference = $oldErrorAction
}

# Step 2: Install Docker using plink (supports -pw for password)
Write-Output "`n[2/5] Installing Docker..."
$installDockerScript = "apt-get update && apt-get install -y ca-certificates curl gnupg lsb-release && install -m 0755 -d /etc/apt/keyrings && rm -f /etc/apt/keyrings/docker.gpg && curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --batch --yes --dearmor -o /etc/apt/keyrings/docker.gpg && chmod a+r /etc/apt/keyrings/docker.gpg && echo 'deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu noble stable' | tee /etc/apt/sources.list.d/docker.list > /dev/null && apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin && (systemctl enable docker || true) && (systemctl start docker || true) && echo 'Docker installation completed'"

Invoke-Plink $installDockerScript

if ($LASTEXITCODE -ne 0)
{
    Write-Output "  Note: Docker may already be installed, continuing..."
}

# Step 3: Create directories and copy files
Write-Output "`n[3/5] Copying files to VDS..."
Invoke-Plink "mkdir -p /opt/aichallenge/{server,mcp-vdsina,frontend,config}"

# Copy files using pscp (PuTTY's scp) from Windows
Write-Output "  Copying server files..."
Invoke-Pscp -Source "$ProjectRoot\server\build\install\server\*" -Dest "/opt/aichallenge/server/" -Recursive -Compress
if ($LASTEXITCODE -ne 0)
{
    throw "Failed to copy server files"
}

Write-Output "  Copying MCP VDSina files..."
Invoke-Pscp -Source "$ProjectRoot\mcp\vdsina\build\install\vdsina\*" -Dest "/opt/aichallenge/mcp-vdsina/" -Recursive -Compress
if ($LASTEXITCODE -ne 0)
{
    throw "Failed to copy MCP VDSina files"
}

Write-Output "  Copying frontend files..."
Invoke-Pscp -Source "$ProjectRoot\composeApp\build\dist\wasmJs\productionExecutable\*" -Dest "/opt/aichallenge/frontend/" -Recursive
if ($LASTEXITCODE -ne 0)
{
    throw "Failed to copy frontend files"
}

Write-Output "  Copying configuration files..."
Invoke-Pscp -Source "$ProjectRoot\deploy\docker-compose.yml" -Dest "/opt/aichallenge/"
Invoke-Pscp -Source "$ProjectRoot\deploy\nginx.conf" -Dest "/opt/aichallenge/config/"
Invoke-Pscp -Source "$EnvFile" -Dest "/opt/aichallenge/.env"

# Set execute permissions on server binaries
Write-Output "  Setting execute permissions..."
Invoke-Plink "chmod +x /opt/aichallenge/server/bin/* /opt/aichallenge/mcp-vdsina/bin/*"

# Step 4: Start services
Write-Output "`n[4/5] Starting services..."
Invoke-Plink "cd /opt/aichallenge && docker compose up -d"
if ($LASTEXITCODE -ne 0) { throw "Docker compose failed" }

# Step 5: Verify deployment
Write-Output "`n[5/5] Verifying deployment..."
Start-Sleep -Seconds 10
try
{
    $healthCheck = Invoke-RestMethod -Uri "http://$ServerIP/api" -TimeoutSec 30 -ErrorAction SilentlyContinue
    if ($healthCheck)
    {
        Write-Output "`n=== Deployment Successful ==="
        Write-Output "Frontend: http://$ServerIP"
        Write-Output "API: http://$ServerIP/api"
    }
    else
    {
        Write-Output "Warning: Health check failed, but services may still be starting"
    }
}
catch
{
    Write-Output "Warning: Health check failed: $_"
}

Write-Output "`nDeploy completed"
