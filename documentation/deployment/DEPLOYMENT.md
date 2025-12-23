# Deployment Guide

## Overview

This guide covers deploying AiChallenge_One to production environments. The application consists of multiple services
that can be deployed independently or as a cohesive system.

⚠️ **Important**: The current codebase is configured for development only. Production deployment requires security
hardening and configuration changes detailed below.

## System Components

### Core Services

1. **Main Server** (port 8080) - AI chat orchestration
2. **Frontend** (static files) - Compose Multiplatform web UI

### Backend Services

3. **Notes Service** (port 8084) - Notes CRUD API
4. **News CRUD Service** (port 8087) - News articles API
5. **Vectorizer Service** (port 8090) - Text embeddings
6. **RAG Service** (port 8091) - Semantic search

### MCP Servers

7. **MCP Notes** (ports 8082/8443) - Notes + Currency tools
8. **MCP NewsAPI** (ports 8085/8444) - External news fetching
9. **MCP NewsCRUD** (ports 8086/8445) - News CRUD tools
10. **MCP Notes Polling** (ports 8088/8447) - Scheduler management

### Dependencies

- **PostgreSQL** (3 databases: chat history, notes, news, vectors)
- **Ollama** (embedding generation)
- **Docker** (for MCP Notes Polling)

## Deployment Strategies

### Strategy 1: Monolithic (All-in-One Server)

**Best For**: Small deployments, proof-of-concept

**Architecture**:

```
Single Server
├── All Services (Gradle multi-module build)
├── PostgreSQL (Docker or managed service)
├── Ollama (local or API)
└── Nginx (reverse proxy + static files)
```

**Pros**:

- Simple deployment
- Low cost
- Easy debugging

**Cons**:

- Single point of failure
- Limited scalability
- Resource competition

### Strategy 2: Microservices (Distributed)

**Best For**: Production, scalability

**Architecture**:

```
Load Balancer (AWS ALB, nginx)
├── Main Server (multiple instances)
├── Services Cluster
│   ├── Notes Service (auto-scaling)
│   ├── News CRUD Service (auto-scaling)
│   ├── Vectorizer Service (GPU instances)
│   └── RAG Service (high memory)
├── MCP Servers Cluster
│   └── MCP servers (multiple instances)
├── Database Layer
│   ├── PostgreSQL Primary
│   ├── PostgreSQL Replicas (read-only)
│   └── Redis (session cache)
└── Static CDN (CloudFront, Cloudflare)
    └── Frontend assets
```

**Pros**:

- Independent scaling
- Fault isolation
- Technology flexibility

**Cons**:

- Complex orchestration
- Higher costs
- Network latency

### Strategy 3: Hybrid (Core + Services)

**Best For**: Medium-sized deployments

**Architecture**:

```
Core Server (Main + Frontend)
├── Managed Services
│   ├── AWS RDS (PostgreSQL)
│   ├── ElastiCache (Redis)
│   └── S3 + CloudFront (static assets)
└── Backend Services (Docker Compose or ECS)
    ├── Notes/News/Vector/RAG services
    └── MCP servers
```

## Production Checklist

### Security Hardening

#### 1. Enable HTTPS

```kotlin
// server/src/main/resources/application.conf
ktor {
  deployment {
    port = 443
    sslPort = 443
  }
  security {
    ssl {
      keyStore = file("keystore.jks")
      keyAlias = "production"
      keyStorePassword = ${SSL_KEYSTORE_PASSWORD}
      privateKeyPassword = ${SSL_KEY_PASSWORD}
    }
  }
}
```

**Generate Production Certificate**:

```bash
# Using Let's Encrypt (recommended)
certbot certonly --standalone -d yourdomain.com

# Convert to JKS
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out keystore.p12
keytool -importkeystore -srckeystore keystore.p12 -srcstoretype PKCS12 -destkeystore keystore.jks
```

#### 2. Restrict CORS

```kotlin
// Replace anyHost() with whitelist
install(CORS) {
    allowHost("yourdomain.com", schemes = listOf("https"))
    allowHost("www.yourdomain.com", schemes = listOf("https"))
    allowCredentials = true
}
```

#### 3. Add Authentication

**Option A: API Key (Simple)**

```kotlin
install(Authentication) {
    apiKey("api-key") {
        validate { credential ->
            if (credential.value == System.getenv("API_KEY")) {
                Principal("api-user")
            } else null
        }
    }
}

routing {
    authenticate("api-key") {
        route("/api") {
            // Protected routes
        }
    }
}
```

**Option B: JWT (Recommended)**

```kotlin
install(Authentication) {
    jwt("auth-jwt") {
        realm = "AiChallenge"
        verifier(makeJwtVerifier())
        validate { credential ->
            if (credential.payload.getClaim("username").asString() != "") {
                JWTPrincipal(credential.payload)
            } else null
        }
    }
}
```

#### 4. Rate Limiting

```kotlin
// Add Ktor rate limiting plugin
install(RateLimiting) {
    register {
        rateLimiter(limit = 100, refillPeriod = 60.seconds)
    }
}
```

#### 5. Input Validation

```kotlin
// Validate message length
if (request.text.length > 10000) {
    call.respond(HttpStatusCode.BadRequest, "Message too long")
    return@post
}

// Sanitize inputs
val sanitized = request.text.replace(Regex("[<>]"), "")
```

#### 6. Secrets Management

**Never commit secrets!**

```bash
# Use environment variables
export GIGACHAT_CLIENT_ID="..."
export GIGACHAT_CLIENT_SECRET="..."
export DATABASE_PASSWORD="..."

# Or use secrets manager
# AWS Secrets Manager, HashiCorp Vault, etc.
```

### Database Configuration

#### PostgreSQL Production Setup

```bash
# Create separate databases
createdb chathistory
createdb notesdb
createdb newsdb
createdb vectordb

# Create users with limited permissions
CREATE USER chatuser WITH PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE chathistory TO chatuser;

# Enable connection pooling (via PgBouncer)
# Install PgBouncer
sudo apt-get install pgbouncer

# Configure /etc/pgbouncer/pgbouncer.ini
[databases]
chathistory = host=localhost port=5432 dbname=chathistory
notesdb = host=localhost port=5432 dbname=notesdb

[pgbouncer]
listen_port = 6432
pool_mode = transaction
max_client_conn = 1000
default_pool_size = 25
```

#### Database Backups

```bash
# Automated daily backups
crontab -e

# Add:
0 2 * * * pg_dump chathistory | gzip > /backups/chathistory-$(date +\%Y\%m\%d).sql.gz
0 2 * * * pg_dump notesdb | gzip > /backups/notesdb-$(date +\%Y\%m\%d).sql.gz
```

**AWS RDS**: Enable automated backups (7-35 day retention)

### Application Configuration

#### Environment-Based Config

```bash
# production.conf
include "application.conf"

ktor {
  deployment {
    port = 8080
    port = ${?PORT}
  }
}

# Override dev settings
database {
  url = ${DATABASE_URL}
  maxPoolSize = 50  # Increase for production
}
```

**Run with production config**:

```bash
java -jar server.jar -config=production.conf
```

## Deployment Methods

### Method 1: Traditional Server (VM)

#### Prerequisites

- Ubuntu 20.04+ / Amazon Linux 2
- Java 17+
- PostgreSQL 15+
- Nginx
- Systemd

#### Steps

**1. Install Dependencies**

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Java
sudo apt install openjdk-17-jdk -y

# Install PostgreSQL
sudo apt install postgresql postgresql-contrib -y

# Install Nginx
sudo apt install nginx -y

# Install Ollama (for embeddings)
curl https://ollama.ai/install.sh | sh
ollama pull nomic-embed-text
```

**2. Build Application**

```bash
# On local machine
.\gradlew.bat :server:installDist
.\gradlew.bat :composeApp:wasmJsBrowserDistribution

# Create deployment package
tar -czf aichallenge-server.tar.gz server/build/install/server/
tar -czf aichallenge-frontend.tar.gz composeApp/build/dist/wasmJs/productionExecutable/
```

**3. Deploy to Server**

```bash
# Upload files
scp aichallenge-server.tar.gz user@server:/opt/aichallenge/
scp aichallenge-frontend.tar.gz user@server:/var/www/aichallenge/

# SSH to server
ssh user@server

# Extract
cd /opt/aichallenge
tar -xzf aichallenge-server.tar.gz

cd /var/www/aichallenge
tar -xzf aichallenge-frontend.tar.gz
```

**4. Configure Systemd Service**

```bash
# Create service file
sudo nano /etc/systemd/system/aichallenge.service
```

```ini
[Unit]
Description=AiChallenge Server
After=network.target postgresql.service

[Service]
Type=simple
User=aichallenge
WorkingDirectory=/opt/aichallenge/server
ExecStart=/opt/aichallenge/server/bin/server
Restart=on-failure
RestartSec=10

Environment="GIGACHAT_CLIENT_ID=your-id"
Environment="GIGACHAT_CLIENT_SECRET=your-secret"
Environment="DATABASE_URL=jdbc:postgresql://localhost:5432/chathistory"
Environment="DATABASE_PASSWORD=secure_password"

[Install]
WantedBy=multi-user.target
```

```bash
# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable aichallenge
sudo systemctl start aichallenge

# Check status
sudo systemctl status aichallenge
```

**5. Configure Nginx**

```bash
sudo nano /etc/nginx/sites-available/aichallenge
```

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    # Redirect to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # Frontend static files
    location / {
        root /var/www/aichallenge;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API proxy
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts for long AI requests
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
}
```

```bash
# Enable site
sudo ln -s /etc/nginx/sites-available/aichallenge /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Method 2: Docker Compose

**Best For**: Development, small production deployments

#### docker-compose.yml

```yaml
version: '3.8'

services:
  # PostgreSQL
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Ollama
  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama_data:/root/.ollama
    ports:
      - "11434:11434"
    command: serve
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]

  # Main Server
  server:
    build:
      context: .
      dockerfile: server/Dockerfile
    environment:
      GIGACHAT_CLIENT_ID: ${GIGACHAT_CLIENT_ID}
      GIGACHAT_CLIENT_SECRET: ${GIGACHAT_CLIENT_SECRET}
      DATABASE_URL: jdbc:postgresql://postgres:5432/chathistory
      DATABASE_PASSWORD: ${DB_PASSWORD}
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

  # Notes Service
  notes-service:
    build:
      context: .
      dockerfile: services/notes/Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/notesdb
      DATABASE_PASSWORD: ${DB_PASSWORD}
    ports:
      - "8084:8084"
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

  # Vectorizer Service
  vectorizer:
    build:
      context: .
      dockerfile: services/vectorizer/Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/vectordb
      DATABASE_PASSWORD: ${DB_PASSWORD}
      OLLAMA_BASE_URL: http://ollama:11434
    ports:
      - "8090:8090"
    depends_on:
      - postgres
      - ollama
    restart: unless-stopped

  # RAG Service
  rag:
    build:
      context: .
      dockerfile: services/rag/Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/vectordb
      DATABASE_PASSWORD: ${DB_PASSWORD}
      VECTORIZER_URL: http://vectorizer:8090
    ports:
      - "8091:8091"
    depends_on:
      - postgres
      - vectorizer
    restart: unless-stopped

  # Nginx (Reverse Proxy + Static Files)
  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./composeApp/build/dist/wasmJs/productionExecutable:/usr/share/nginx/html
      - /etc/letsencrypt:/etc/letsencrypt:ro
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - server
    restart: unless-stopped

volumes:
  postgres_data:
  ollama_data:
```

#### .env file

```bash
# Never commit this file!
GIGACHAT_CLIENT_ID=your-gigachat-id
GIGACHAT_CLIENT_SECRET=your-gigachat-secret
DB_PASSWORD=secure_database_password
OPENAI_API_KEY=your-openrouter-key
NEWSAPI_API_KEY=your-newsapi-key
```

#### Deployment Commands

```bash
# Build images
docker-compose build

# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Update and restart
git pull
docker-compose build
docker-compose up -d
```

### Method 3: Kubernetes (EKS/GKE/AKS)

**Best For**: Large-scale production, high availability

#### Prerequisites

- Kubernetes cluster (EKS, GKE, or AKS)
- kubectl configured
- Docker registry (ECR, GCR, Docker Hub)
- Helm (package manager)

#### Directory Structure

```
k8s/
├── namespace.yaml
├── configmaps/
├── secrets/
├── deployments/
│   ├── server.yaml
│   ├── notes-service.yaml
│   ├── vectorizer.yaml
│   └── rag.yaml
├── services/
│   ├── server-service.yaml
│   └── ...
├── ingress.yaml
└── hpa/ (HorizontalPodAutoscaler)
```

#### Example Deployment

**server-deployment.yaml**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: aichallenge-server
  namespace: aichallenge
spec:
  replicas: 3
  selector:
    matchLabels:
      app: aichallenge-server
  template:
    metadata:
      labels:
        app: aichallenge-server
    spec:
      containers:
      - name: server
        image: your-registry/aichallenge-server:latest
        ports:
        - containerPort: 8080
        env:
        - name: GIGACHAT_CLIENT_ID
          valueFrom:
            secretKeyRef:
              name: gigachat-credentials
              key: client-id
        - name: GIGACHAT_CLIENT_SECRET
          valueFrom:
            secretKeyRef:
              name: gigachat-credentials
              key: client-secret
        - name: DATABASE_URL
          valueFrom:
            configMapKeyRef:
              name: database-config
              key: url
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: aichallenge-server
  namespace: aichallenge
spec:
  selector:
    app: aichallenge-server
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: aichallenge-server-hpa
  namespace: aichallenge
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: aichallenge-server
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Deploy**:

```bash
# Create namespace
kubectl create namespace aichallenge

# Create secrets
kubectl create secret generic gigachat-credentials \
  --from-literal=client-id='your-id' \
  --from-literal=client-secret='your-secret' \
  -n aichallenge

# Apply configurations
kubectl apply -f k8s/

# Check status
kubectl get pods -n aichallenge
kubectl get svc -n aichallenge

# View logs
kubectl logs -f deployment/aichallenge-server -n aichallenge
```

## Monitoring & Observability

### Prometheus + Grafana

**Install Prometheus Operator**:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
```

**Add Metrics Endpoint to Ktor**:

```kotlin
// Add to server/build.gradle.kts
implementation("io.ktor:ktor-server-metrics-micrometer:$ktor_version")
implementation("io.micrometer:micrometer-registry-prometheus:$micrometer_version")

// In Application.kt
install(MicrometerMetrics) {
    registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

routing {
    get("/metrics") {
        call.respond(registry.scrape())
    }
}
```

**Grafana Dashboard**: Import dashboard ID 4701 (JVM metrics)

### Logging

**Centralized Logging with ELK**:

```bash
# Logstash config for Ktor
input {
  tcp {
    port => 5000
    codec => json
  }
}

filter {
  if [logger_name] =~ "ru.sber.cb.aichallenge_one" {
    mutate {
      add_tag => ["aichallenge"]
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "aichallenge-%{+YYYY.MM.dd}"
  }
}
```

## Rollback Strategy

### Docker Compose

```bash
# Tag images with version
docker-compose build --no-cache
docker tag aichallenge-server:latest aichallenge-server:v1.2.0

# Rollback
docker-compose down
# Edit docker-compose.yml to use v1.1.0
docker-compose up -d
```

### Kubernetes

```bash
# Rollout new version
kubectl set image deployment/aichallenge-server server=your-registry/server:v1.2.0 -n aichallenge

# Check rollout
kubectl rollout status deployment/aichallenge-server -n aichallenge

# Rollback to previous version
kubectl rollout undo deployment/aichallenge-server -n aichallenge

# Rollback to specific revision
kubectl rollout undo deployment/aichallenge-server --to-revision=2 -n aichallenge
```

## Cost Optimization

### AWS Estimated Costs (Medium Deployment)

| Service                            | Configuration          | Monthly Cost   |
|------------------------------------|------------------------|----------------|
| EC2 (t3.large x2)                  | Main Server + Services | $120           |
| RDS PostgreSQL (db.t3.medium)      | Multi-AZ               | $100           |
| ElastiCache Redis (cache.t3.micro) | Session cache          | $15            |
| ALB                                | Load balancer          | $25            |
| S3 + CloudFront                    | Static assets          | $10            |
| **Total**                          |                        | **$270/month** |

### Free Tier Options

**Minimal Deployment** (Free/Low Cost):

- Fly.io: Free tier (1 shared CPU, 256MB RAM)
- Heroku: Free dyno (deprecated, use Render.com instead)
- Railway: $5/month hobby plan
- Render: Free tier available
- Supabase: Free PostgreSQL (500MB)
- Vercel: Free static hosting

## Troubleshooting Production Issues

### Issue: High Memory Usage

**Symptoms**: OOM errors, container restarts

**Solutions**:

1. Increase JVM heap: `JAVA_OPTS="-Xmx2g"`
2. Enable G1GC: `JAVA_OPTS="-XX:+UseG1GC"`
3. Increase container memory limits
4. Review conversation history size (implement summarization/cleanup)

### Issue: Database Connection Pool Exhausted

**Symptoms**: "Too many connections" errors

**Solutions**:

1. Increase HikariCP pool size: `maxPoolSize = 50`
2. Use PgBouncer for connection pooling
3. Review long-running queries
4. Enable connection timeout

### Issue: Slow AI Responses

**Symptoms**: Requests timeout, high latency

**Solutions**:

1. Increase Ktor timeout: `requestTimeoutMillis = 300000`
2. Implement response caching (Redis)
3. Use faster AI models
4. Enable request queuing

## Related Documentation

- [Architecture Overview](../ARCHITECTURE.md) - System design
- [Getting Started](../GETTING-STARTED.md) - Development setup
- [Server Module](../modules/server.md) - Main server configuration

## References

- [Ktor Deployment](https://ktor.io/docs/deploy.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
