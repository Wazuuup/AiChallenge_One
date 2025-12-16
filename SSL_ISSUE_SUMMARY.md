# SSL Certificate Issue - Summary and Solutions

## Problem

`ExchangeRateClientSSL` fails with:

```
io.ktor.network.tls.TLSException: No server host: 89.124.67.120 in the server certificate.
The certificate was issued for: 127.0.0.1
```

## Root Cause

1. **Current certificate** only includes `127.0.0.1`
2. **Client is trying to connect** to `89.124.67.120` (remote server)
3. **Ktor CIO engine** performs strict hostname verification and cannot be fully disabled

## Solutions

### Solution 1: Use localhost (Quick Fix)

**When:** Testing with server running locally

**Status:** ✅ READY TO USE

```bash
# Server runs on localhost
.\gradlew.bat :mcp-server:run

# In another terminal - client connects to localhost
.\gradlew.bat :mcp-client:runExchangeRateSSL
```

This works because:

- URL changed to `https://127.0.0.1:8443`
- Certificate has `127.0.0.1` ✓
- Hostname matches ✓

### Solution 2: Regenerate Certificate (Proper Fix)

**When:** Connecting to remote server at `89.124.67.120`

**Steps:**

#### Step 1: Regenerate keystore with all domains

```bash
rebuild-with-new-cert.bat
```

This will:

1. Delete old keystore
2. Rebuild mcp-server
3. Generate new certificate with:
    - `89.124.67.120` ← remote IP
    - `127.0.0.1` ← localhost
    - `localhost`
    - `0.0.0.0`
    - `v573465.hosted-by-vdsina.com` ← remote hostname

#### Step 2: Switch client to remote URL

```bash
switch-to-remote-ssl.bat
```

Or manually edit `mcp-client/src/main/kotlin/.../Application.kt`:

```kotlin
const val MCP_SERVER_SSL_URL = "https://89.124.67.120:8443"
```

#### Step 3: Rebuild and test

```bash
.\gradlew.bat :mcp-client:build
.\gradlew.bat :mcp-client:runExchangeRateSSL
```

#### Step 4: Deploy to remote server

```bash
deploy-mcp-server-simple.bat
```

## Why Setting `serverName = null` Doesn't Work

**Technical reason:**

- Ktor CIO engine performs hostname verification at the **TLS handshake level**
- This happens before the application layer
- The `serverName` parameter only controls SNI (Server Name Indication)
- It does **not** disable the hostname verification in `verifyHostnameInCertificate()`

**The only way** to avoid hostname verification errors with Ktor CIO is to:

1. Ensure certificate includes the hostname/IP you're connecting to
2. Or use a different HTTP client library (not practical with MCP SDK)

## Current State

✅ **Working:** localhost connection (`127.0.0.1:8443`)
❌ **Not working:** remote connection (`89.124.67.120:8443`) - needs certificate regeneration

## Next Steps

### For Local Testing:

```bash
.\gradlew.bat :mcp-server:run
.\gradlew.bat :mcp-client:runExchangeRateSSL
```

### For Remote Server:

```bash
# 1. Generate proper certificate
rebuild-with-new-cert.bat

# 2. Switch to remote URL
switch-to-remote-ssl.bat

# 3. Test locally first
.\gradlew.bat :mcp-server:run
.\gradlew.bat :mcp-client:runExchangeRateSSL

# 4. Deploy to remote
deploy-mcp-server-simple.bat
```

## Files Modified

1. ✅ `mcp-server/.../Application.kt` - Updated certificate domains list
2. ✅ `mcp-client/.../Application.kt` - Changed URL to `127.0.0.1` (temporary)
3. ✅ `mcp-client/.../ExchangeRateClientSSL.kt` - Improved SSL configuration
4. ✅ `deploy-remote.sh` - Auto-delete old keystore on deployment

## Production Recommendations

For production deployment:

1. **Use real SSL certificate** from Certificate Authority:
    - Let's Encrypt (free)
    - DigiCert, Sectigo, etc. (paid)

2. **Remove trust-all certificate code:**
   ```kotlin
   // DELETE THIS IN PRODUCTION:
   trustManager = object : X509TrustManager { ... }
   ```

3. **Use proper certificate validation:**
    - Let system validate certificates
    - Don't bypass hostname verification
    - Use HTTPS only (disable HTTP)

See `SSL_README.md` for details.
