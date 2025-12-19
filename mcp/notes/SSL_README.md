# MCP Server SSL/HTTPS Configuration

This document describes how SSL/HTTPS support is configured for the MCP server.

## Overview

The MCP server **ALWAYS** runs with both HTTP and HTTPS enabled:

- **HTTP**: `http://localhost:8082` (always enabled)
- **HTTPS**: `https://localhost:8443` (always enabled)

## SSL Auto-Generation

The SSL certificate is **automatically generated** on first startup if it doesn't exist:

- A self-signed certificate is created programmatically using Ktor's `buildKeyStore` function
- No manual keystore generation required
- Stored at: `mcp-server/src/main/resources/keystore.jks`

## SSL Certificate

A self-signed SSL certificate is automatically generated on first startup:

- **Location**: `mcp-server/src/main/resources/keystore.jks`
- **Alias**: `mcpserver` (configurable via `SSL_KEY_ALIAS`)
- **Password**: `changeit` (both keystore and private key, configurable)
- **Domains**: `127.0.0.1`, `localhost`, `0.0.0.0`, `89.124.67.120`, `v573465.hosted-by-vdsina.com`
- **Generation**: Automatic using Ktor's `buildKeyStore` API

## Configuration

### Environment Variables

You can customize SSL configuration using these environment variables:

| Variable                | Default     | Description          |
|-------------------------|-------------|----------------------|
| `SSL_KEY_ALIAS`         | `mcpserver` | Keystore alias name  |
| `SSL_KEYSTORE_PASSWORD` | `changeit`  | Keystore password    |
| `SSL_KEY_PASSWORD`      | `changeit`  | Private key password |

**Note**: SSL is always enabled and cannot be disabled.

### Example: Custom Password

```bash
# Windows
set SSL_KEYSTORE_PASSWORD=myCustomPassword
set SSL_KEY_PASSWORD=myCustomPassword
.\gradlew.bat :mcp-server:run

# Linux/Mac
export SSL_KEYSTORE_PASSWORD=myCustomPassword
export SSL_KEY_PASSWORD=myCustomPassword
./gradlew :mcp-server:run
```

## Regenerating the SSL Certificate

If you need to regenerate the SSL certificate (e.g., it expired or you want different settings):

### Windows

```bash
keytool -genkeypair -keyalg RSA -keysize 2048 -validity 365 -alias mcpserver ^
  -keystore mcp-server\src\main\resources\keystore.jks ^
  -storepass changeit -keypass changeit ^
  -dname "CN=localhost, OU=MCP Server, O=AiChallenge, L=Moscow, ST=Moscow, C=RU"
```

### Linux/Mac

```bash
keytool -genkeypair -keyalg RSA -keysize 2048 -validity 365 -alias mcpserver \
  -keystore mcp-server/src/main/resources/keystore.jks \
  -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=MCP Server, O=AiChallenge, L=Moscow, ST=Moscow, C=RU"
```

### Custom Certificate Parameters

You can customize the certificate by modifying the keytool parameters:

- `-validity 365`: Change number of days (e.g., `-validity 730` for 2 years)
- `-keysize 2048`: Change key size (e.g., `-keysize 4096` for stronger encryption)
- `-dname`: Change certificate distinguished name fields:
    - `CN` (Common Name): hostname (use `localhost` for local development)
    - `OU` (Organizational Unit): department/division
    - `O` (Organization): company name
    - `L` (Locality): city
    - `ST` (State): state/province
    - `C` (Country): 2-letter country code

## Running the Server

### Start the Server (SSL Always Enabled)

```bash
.\gradlew.bat :mcp-server:run
```

The server will always start on both:

- **HTTP**: `http://localhost:8082`
- **HTTPS**: `https://localhost:8443`

On first startup, you'll see:

```
SSL keystore not found. Generating self-signed certificate...
✓ SSL keystore generated at: <path>/mcp-server/src/main/resources/keystore.jks
```

Subsequent startups will use the existing keystore.

## Client Configuration

### HTTP Client (Original)

```bash
.\gradlew.bat :mcp-client:runExchangeRate
```

Connects to: `http://127.0.0.1:8082`

### HTTPS/SSL Client

```bash
.\gradlew.bat :mcp-client:runExchangeRateSSL
```

Connects to: `https://127.0.0.1:8443`

## Security Considerations

### Development vs Production

⚠️ **IMPORTANT**: The current SSL setup is designed for **DEVELOPMENT ONLY**

**Development Settings:**

- Self-signed certificate (not trusted by browsers/clients by default)
- Default passwords (`changeit`)
- Client configured to trust all certificates (insecure)

**For Production, you MUST:**

1. **Use a proper SSL certificate:**
    - Obtain from a Certificate Authority (CA) like Let's Encrypt, DigiCert, etc.
    - Or use a certificate signed by your organization's internal CA

2. **Secure passwords:**
    - Change default passwords (`changeit`)
    - Store passwords securely (environment variables, secrets manager)
    - Never commit passwords to version control

3. **Client certificate validation:**
    - Remove the `trustManager` that accepts all certificates
    - Configure proper certificate validation
    - Example of insecure code to remove:
      ```kotlin
      // DO NOT USE IN PRODUCTION!
      trustManager = object : X509TrustManager {
          override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
          override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
          override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
      }
      ```

4. **Additional security measures:**
    - Enable HTTPS only (disable HTTP in production)
    - Use strong TLS versions (TLS 1.2+)
    - Configure cipher suites
    - Implement HSTS (HTTP Strict Transport Security)
    - Regular certificate renewal
    - Monitor certificate expiration

## Troubleshooting

### Certificate Not Trusted Error

If you see SSL/certificate trust errors:

**In Browser:**

- You'll see a security warning
- Click "Advanced" → "Proceed to localhost (unsafe)" (development only!)

**In Client:**

- The SSL client (`ExchangeRateClientSSL`) is configured to trust self-signed certificates
- For production, replace with proper certificate validation

### Port Already in Use

If port 8443 is already in use:

```bash
# Windows - Find process using port 8443
netstat -ano | findstr :8443
taskkill /PID <process_id> /F

# Linux/Mac - Find and kill process
lsof -i :8443
kill -9 <process_id>
```

Or change the port in `Application.kt`:

```kotlin
const val MCP_SERVER_SSL_PORT = 8444  // or any available port
```

### Certificate Expired

If the certificate expires (default: 365 days), regenerate it using the keytool command above with a new validity
period.

## Verifying SSL Connection

### Using curl

```bash
# HTTP (works with both SSL enabled/disabled)
curl http://localhost:8082/health

# HTTPS (requires -k flag for self-signed cert)
curl -k https://localhost:8443/health
```

### Using OpenSSL

```bash
# Check certificate details
openssl s_client -connect localhost:8443 -showcerts
```

### In Browser

Navigate to:

- HTTP: http://localhost:8082/health
- HTTPS: https://localhost:8443/health

## Files Modified

SSL support required changes to these files:

1. **mcp-server/src/main/kotlin/.../Application.kt**
    - Added SSL connector configuration
    - Added auto-detection logic

2. **mcp-server/src/main/resources/application.conf**
    - Added SSL configuration documentation

3. **mcp-server/src/main/resources/keystore.jks**
    - Generated self-signed SSL certificate

4. **mcp-client/src/main/kotlin/.../ExchangeRateClientSSL.kt**
    - New SSL-enabled client implementation

5. **mcp-client/build.gradle.kts**
    - Added `runExchangeRateSSL` task

## References

- [Ktor SSL/TLS Documentation](https://ktor.io/docs/ssl.html)
- [Java Keytool Documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
- [Let's Encrypt (Free SSL Certificates)](https://letsencrypt.org/)
