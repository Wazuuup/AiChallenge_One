# MCP Server Deployment Guide

This guide explains how to deploy the MCP server to a remote Ubuntu server at 89.124.67.120.

## Prerequisites

### Local Machine (Windows)

- OpenSSH client (included in Windows 10/11)
- tar command (included in Windows 10/11)
- Gradle (via gradlew.bat)

### Remote Server (Ubuntu)

- Ubuntu Linux
- SSH access with username `mcp`
- sudo privileges (for installing Java if needed)

## Deployment Scripts

### Option 1: Simple Deployment (Recommended)

Use `deploy-mcp-server-simple.bat` - works with standard Windows OpenSSH.

```bash
deploy-mcp-server-simple.bat
```

**Note:** You will be prompted for the SSH password multiple times (5-6 times) during deployment.

### Option 2: Password-Based Deployment

Use `deploy-mcp-server.bat` with password parameter. Requires `sshpass` (not commonly available on Windows).

```bash
deploy-mcp-server.bat YOUR_PASSWORD
```

## What the Script Does

1. **Builds the project locally**
    - Cleans and builds mcp-server module
    - Creates distribution in `mcp-server/build/install/mcp-server`

2. **Creates deployment archive**
    - Packages the distribution as `mcp-server-deploy.tar.gz`

3. **Transfers files to remote server**
    - Uploads archive to `/tmp/` on remote server
    - Uploads deployment helper scripts

4. **Prepares remote environment**
    - Installs Java 17 if not present
    - Creates deployment directory at `/home/mcp/mcp-server`
    - Stops any existing mcp-server instance

5. **Deploys and starts server**
    - Extracts archive to deployment directory
    - Sets up environment variables
    - Starts server in background with nohup
    - Saves PID to `mcp-server.pid`

6. **Verifies deployment**
    - Checks if server process is running
    - Displays server URLs and useful commands

## Server Configuration

The server runs with the following configuration:

- **HTTP Port:** 8082
- **HTTPS Port:** 8443
- **Deployment Directory:** `/home/mcp/mcp-server`
- **Log File:** `/home/mcp/mcp-server/logs/server.log`
- **PID File:** `/home/mcp/mcp-server/mcp-server.pid`

### Environment Variables

The server uses these SSL configuration variables:

- `SSL_KEY_ALIAS=mcpserver`
- `SSL_KEYSTORE_PASSWORD=changeit`
- `SSL_KEY_PASSWORD=changeit`

The keystore is auto-generated on first run if not present.

## Post-Deployment Commands

### View Server Logs

```bash
ssh mcp@89.124.67.120 "tail -f /home/mcp/mcp-server/logs/server.log"
```

### Check Server Status

```bash
ssh mcp@89.124.67.120 "ps -p \$(cat /home/mcp/mcp-server/mcp-server.pid)"
```

### Stop Server

```bash
ssh mcp@89.124.67.120 "kill \$(cat /home/mcp/mcp-server/mcp-server.pid)"
```

### Restart Server

```bash
ssh mcp@89.124.67.120 "cd /home/mcp/mcp-server && nohup bin/mcp-server > logs/server.log 2>&1 & echo \$! > mcp-server.pid"
```

### Test Server

```bash
# HTTP endpoint
curl http://89.124.67.120:8082

# HTTPS endpoint (self-signed certificate)
curl -k https://89.124.67.120:8443
```

## Setting Up SSH Key Authentication (Recommended)

To avoid entering password multiple times, set up SSH key authentication:

### Automated Setup (Easy)

```bash
setup-ssh-key.bat
```

This script will:

- Generate SSH key if you don't have one
- Copy it to the remote server
- Test the connection

### Manual Setup

#### 1. Generate SSH Key (if you don't have one)

```bash
ssh-keygen -t ed25519 -C "your_email@example.com"
```

Press Enter to accept default location (`%USERPROFILE%\.ssh\id_ed25519`), optionally set a passphrase.

#### 2. Copy Public Key to Server

```bash
type %USERPROFILE%\.ssh\id_ed25519.pub | ssh mcp@89.124.67.120 "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
```

You will be prompted for your SSH password once.

#### 3. Test Connection

```bash
ssh mcp@89.124.67.120
```

You should connect without password prompt.

## Troubleshooting

### Build Fails

- Ensure you have Java 17+ installed locally
- Check that all dependencies are available
- Try running `.\gradlew.bat clean build` manually

### Connection Refused

- Verify SSH service is running on remote server
- Check firewall rules allow SSH (port 22)
- Confirm IP address and username are correct

### Server Doesn't Start

1. Check logs:
   ```bash
   ssh mcp@89.124.67.120 "cat /home/mcp/mcp-server/logs/server.log"
   ```

2. Check if Java is installed:
   ```bash
   ssh mcp@89.124.67.120 "java -version"
   ```

3. Check if ports are already in use:
   ```bash
   ssh mcp@89.124.67.120 "netstat -tuln | grep -E '8082|8443'"
   ```

### Ports Not Accessible

- Check firewall rules on remote server:
  ```bash
  ssh mcp@89.124.67.120 "sudo ufw status"
  ```
- Allow ports if needed:
  ```bash
  ssh mcp@89.124.67.120 "sudo ufw allow 8082/tcp && sudo ufw allow 8443/tcp"
  ```

## Helper Scripts

The deployment uses three scripts:

1. **deploy-mcp-server.bat** (or deploy-mcp-server-simple.bat)
    - Main deployment script for Windows
    - Orchestrates build, transfer, and deployment

2. **deploy-remote.sh**
    - Runs on remote server
    - Handles environment setup and extraction

3. **start-remote.sh**
    - Runs on remote server
    - Starts the mcp-server process

## File Structure on Remote Server

```
/home/mcp/mcp-server/
├── bin/
│   └── mcp-server           # Startup script
├── lib/                     # JAR dependencies
├── logs/
│   └── server.log          # Application logs
├── src/
│   └── main/
│       └── resources/
│           ├── application.conf
│           └── keystore.jks (auto-generated)
└── mcp-server.pid          # Process ID file
```

## Security Considerations

**⚠️ IMPORTANT:** This deployment is for development/testing only.

For production deployment, consider:

- Use SSH key authentication (no passwords)
- Configure proper firewall rules
- Use a reverse proxy (nginx/Apache) with proper SSL certificates
- Set up proper logging and monitoring
- Use environment-specific configuration files
- Implement proper secret management
- Enable rate limiting and DDoS protection

## Quick Reference

```bash
# Deploy
deploy-mcp-server-simple.bat

# Check status
ssh mcp@89.124.67.120 "ps aux | grep mcp-server"

# View logs
ssh mcp@89.124.67.120 "tail -f /home/mcp/mcp-server/logs/server.log"

# Stop
ssh mcp@89.124.67.120 "kill \$(cat /home/mcp/mcp-server/mcp-server.pid)"

# Test
curl http://89.124.67.120:8082
```
