#!/bin/bash
set -e

REMOTE_DIR=/home/mcp/mcp-server
ARCHIVE_NAME=mcp-server-deploy.tar.gz

echo "=== Remote Deployment Started ==="

# Install Java if not present
if ! command -v java &> /dev/null; then
    echo "Java not found. Installing OpenJDK 17..."
    sudo apt-get update
    sudo apt-get install -y openjdk-17-jre-headless
else
    echo "Java is already installed: $(java -version 2>&1 | head -n 1)"
fi

# Create deployment directory
echo "Creating deployment directory: $REMOTE_DIR"
mkdir -p $REMOTE_DIR
mkdir -p $REMOTE_DIR/logs

# Stop existing server if running
echo "Stopping existing mcp-server (if running)..."
if [ -f $REMOTE_DIR/mcp-server.pid ]; then
    PID=$(cat $REMOTE_DIR/mcp-server.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "Killing process $PID"
        kill $PID
        sleep 2
        # Force kill if still running
        if ps -p $PID > /dev/null 2>&1; then
            kill -9 $PID
        fi
    fi
    rm -f $REMOTE_DIR/mcp-server.pid
fi

# Extract new version
echo "Extracting new version..."
cd $REMOTE_DIR
tar -xzf /tmp/$ARCHIVE_NAME --strip-components=1
rm -f /tmp/$ARCHIVE_NAME

# Remove old keystore to force regeneration with new domains
echo "Removing old SSL keystore (will be regenerated with correct domains)..."
rm -f src/main/resources/keystore.jks

# Make scripts executable
chmod +x bin/mcp-server

echo "=== Deployment Complete ==="
