# Спецификация: mcp:vdsina

**Версия**: 1.0
**Дата**: 2026-01-16
**Автор**: Claude Opus 4.5

---

## 1. Обзор

### 1.1 Цель

Создание MCP (Model Context Protocol) сервера для работы с VDSina public API. MCP предоставляет инструменты для:

- Управления VDS серверами (создание, статус, удаление)
- Работы со справочниками (датацентры, тарифы, шаблоны ОС)
- Управления SSH ключами
- Автоматизированного развёртывания приложения на созданном VDS

### 1.2 Scope (Что входит)

1. **MCP сервер** `mcp:vdsina` с HTTP/HTTPS endpoints
2. **PowerShell скрипт** для полного цикла deploy
3. **Docker Compose конфигурация** для production
4. **Nginx конфигурация** для reverse proxy
5. **Изменения в сборке** — отключение MCP и services модулей

### 1.3 Out of Scope

- RAG/Vectorizer/Ollama функциональность
- MCP серверы (notes, newsapi, newscrud, notes-polling, rag, git, github-reviewer)
- Services (notes, news-crud, notes-scheduler, vectorizer, rag, github-webhook)
- HTTPS/SSL для production VDS
- Автоматический cleanup/TTL серверов
- Re-deploy на существующий сервер
- Автозапуск при ребуте VDS

---

## 2. Архитектура

### 2.1 Структура модуля

```
mcp/
└── vdsina/
    ├── build.gradle.kts
    └── src/main/
        ├── kotlin/ru/sber/cb/aichallenge_one/mcp_vdsina/
        │   ├── Application.kt
        │   ├── VdsinaMcpConfiguration.kt
        │   ├── service/
        │   │   └── VdsinaApiService.kt
        │   ├── model/
        │   │   └── VdsinaModels.kt
        │   └── di/
        │       └── VdsinaModule.kt
        └── resources/
            ├── application.conf
            ├── application-dev.conf
            └── logback.xml
```

### 2.2 Порты

| Протокол | Порт |
|----------|------|
| HTTP     | 8096 |
| HTTPS    | 8452 |

### 2.3 Зависимости

- Ktor Server (Netty)
- Ktor Client (CIO)
- Koin DI
- kotlinx-serialization-json
- MCP SDK
- shared модуль

---

## 3. Конфигурация

### 3.1 application.conf

```hocon
ktor {
    deployment {
        port = 8096
        port = ${?PORT}
        ssl_port = 8452
        ssl_port = ${?SSL_PORT}
    }
    application {
        modules = [ ru.sber.cb.aichallenge_one.mcp_vdsina.ApplicationKt.module ]
    }
}

vdsina {
    baseUrl = "https://userapi.vdsina.com/v1"
    # API токен из личного кабинета VDSina
    apiToken = ${?VDSINA_API_TOKEN}

    # ID существующего SSH ключа в VDSina
    sshKeyId = ${?VDSINA_SSH_KEY_ID}

    # Минимальные требования к тарифу
    minRamGb = 1
}

deploy {
    # Путь к PowerShell скрипту
    scriptPath = "scripts/deploy-vdsina.ps1"

    # Путь к SSH приватному ключу (по умолчанию ~/.ssh/id_rsa)
    sshKeyPath = ${?SSH_KEY_PATH}
}

ssl {
    keyAlias = "vdsina-mcp"
    keyAlias = ${?SSL_KEY_ALIAS}
    keystorePassword = "changeit"
    keystorePassword = ${?SSL_KEYSTORE_PASSWORD}
    keyPassword = "changeit"
    keyPassword = ${?SSL_KEY_PASSWORD}
}
```

### 3.2 application-dev.conf (не коммитить!)

```hocon
include "application.conf"

vdsina {
    apiToken = "your-vdsina-api-token-here"
    sshKeyId = 12345  # ID вашего SSH ключа в VDSina
}
```

---

## 4. MCP Tools

### 4.1 Справочники

#### `list_datacenters`

Получить список доступных датацентров.

**Параметры**: нет

**Возвращает**:

```json
{
  "datacenters": [
    {
      "id": 1,
      "name": "Amsterdam 1, Netherlands",
      "country": "nl",
      "active": true
    }
  ]
}
```

#### `list_server_plans`

Получить список тарифных планов для группы.

**Параметры**:

- `group_id` (integer, required): ID группы тарифов

**Возвращает**:

```json
{
  "plans": [
    {
      "id": 13,
      "name": "Cloud",
      "cost": 1.55,
      "period": "day",
      "cpu": 1,
      "ram_gb": 2,
      "disk_gb": 40,
      "active": true
    }
  ]
}
```

#### `list_server_groups`

Получить список групп тарифных планов.

**Параметры**: нет

**Возвращает**:

```json
{
  "groups": [
    {
      "id": 13,
      "name": "Standard servers",
      "active": true,
      "description": "Powerful servers based on..."
    }
  ]
}
```

#### `list_templates`

Получить список шаблонов ОС.

**Параметры**: нет

**Возвращает**:

```json
{
  "templates": [
    {
      "id": 1,
      "name": "Ubuntu 24.04 x64",
      "active": true,
      "ssh_key_supported": true
    }
  ]
}
```

### 4.2 SSH ключи

#### `list_ssh_keys`

Получить список SSH ключей аккаунта.

**Параметры**: нет

**Возвращает**:

```json
{
  "keys": [
    {
      "id": 1,
      "name": "My SSH Key"
    }
  ]
}
```

#### `create_ssh_key`

Создать новый SSH ключ.

**Параметры**:

- `name` (string, required): Название ключа
- `data` (string, required): Публичный ключ (ssh-rsa AAAA...)

**Возвращает**:

```json
{
  "id": 3,
  "message": "SSH key created successfully"
}
```

### 4.3 Управление серверами

#### `list_servers`

Получить список всех серверов аккаунта.

**Параметры**: нет

**Возвращает**:

```json
{
  "servers": [
    {
      "id": 12345,
      "name": "Server #12345",
      "status": "active",
      "ip": "91.84.101.78",
      "created": "2025-02-21",
      "datacenter": "Amsterdam 1",
      "plan": "2 RAM / 1 CPU / 40 NVMe"
    }
  ]
}
```

#### `get_server_status`

Получить детальную информацию о сервере.

**Параметры**:

- `server_id` (integer, required): ID сервера

**Возвращает**:

```json
{
  "id": 12345,
  "name": "Server #12345",
  "status": "active",
  "status_text": "",
  "ip": "91.84.101.78",
  "created": "2025-02-21 12:26:32",
  "datacenter": {
    "id": 1,
    "name": "Amsterdam 1, Netherlands"
  },
  "plan": {
    "id": 1,
    "name": "2 RAM / 1 CPU / 40 NVMe"
  },
  "template": {
    "id": 23,
    "name": "Ubuntu 24.04"
  }
}
```

#### `create_server`

Создать новый VDS сервер.

**Алгоритм выбора минимальной конфигурации**:

1. Запросить `GET /server-group` для получения групп
2. Для каждой активной группы запросить `GET /server-plan/{groupID}`
3. Отфильтровать планы: active=true, enable=true, ram >= minRamGb
4. Отсортировать по cost (ascending)
5. Выбрать самый дешёвый план
6. Запросить `GET /datacenter` и выбрать первый активный
7. Запросить `GET /template` и найти Ubuntu 24.04 с ssh-key=true
8. Создать сервер с ssh-key из конфигурации

**Параметры**:

- `name` (string, optional): Название сервера (default: "AiChallenge-{timestamp}")

**Возвращает**:

```json
{
  "server_id": 512348,
  "message": "Server creation started. Use get_server_status to check when status becomes 'active'",
  "estimated_time": "2-5 minutes"
}
```

#### `delete_server`

Удалить VDS сервер.

**Параметры**:

- `server_id` (integer, required): ID сервера

**Возвращает**:

```json
{
  "message": "Server deleted successfully"
}
```

### 4.4 Deploy

#### `deploy_app`

Развернуть приложение на VDS сервере.

**Алгоритм**:

1. Получить список серверов через VDSina API
2. Выбрать последний созданный сервер (сортировка по created desc)
3. Проверить статус = active
4. Получить IP адрес сервера
5. Запустить PowerShell скрипт через ProcessBuilder:
   ```
   powershell.exe -ExecutionPolicy Bypass -File scripts/deploy-vdsina.ps1 -ServerIP <ip>
   ```
6. Дождаться завершения скрипта
7. Вернуть результат

**Параметры**: нет (использует последний созданный сервер)

**Возвращает**:

```json
{
  "success": true,
  "server_ip": "91.84.101.78",
  "message": "Deployment completed successfully",
  "frontend_url": "http://91.84.101.78",
  "api_url": "http://91.84.101.78/api"
}
```

**Ошибки**:

- "No active servers found" - нет серверов в статусе active
- "Server is not ready (status: new)" - сервер ещё создаётся
- "Deployment script failed: <error>" - ошибка выполнения скрипта

---

## 5. Deploy скрипт

### 5.1 scripts/deploy-vdsina.ps1

```powershell
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
```

---

## 6. Docker Compose конфигурация

### 6.1 deploy/docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: aichallenge-db
    environment:
      POSTGRES_DB: aichallenge
      POSTGRES_USER: ${DATABASE_USER:-aichallenge}
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD:-aichallenge}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - aichallenge-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DATABASE_USER:-aichallenge}"]
      interval: 10s
      timeout: 5s
      retries: 5

  server:
    image: amazoncorretto:17-alpine
    container_name: aichallenge-server
    working_dir: /app
    command: ["./bin/server"]
    volumes:
      - ./server:/app:ro
    environment:
      PORT: 8080
      DATABASE_URL: jdbc:postgresql://postgres:5432/aichallenge
      DATABASE_USER: ${DATABASE_USER:-aichallenge}
      DATABASE_PASSWORD: ${DATABASE_PASSWORD:-aichallenge}
      GIGACHAT_CLIENT_ID: ${GIGACHAT_CLIENT_ID}
      GIGACHAT_CLIENT_SECRET: ${GIGACHAT_CLIENT_SECRET}
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      OPENAI_MODEL: ${OPENAI_MODEL:-openai/gpt-3.5-turbo}
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - aichallenge-net

  nginx:
    image: nginx:alpine
    container_name: aichallenge-nginx
    ports:
      - "80:80"
    volumes:
      - ./frontend:/usr/share/nginx/html:ro
      - ./config/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - server
    networks:
      - aichallenge-net

volumes:
  postgres_data:

networks:
  aichallenge-net:
    driver: bridge
```

### 6.2 deploy/nginx.conf

```nginx
events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    sendfile        on;
    keepalive_timeout  65;

    # Wasm support
    types {
        application/wasm wasm;
    }

    server {
        listen 80;
        server_name _;

        root /usr/share/nginx/html;
        index index.html;

        # Frontend (Compose Multiplatform Wasm)
        location / {
            try_files $uri $uri/ /index.html;

            # CORS headers for Wasm
            add_header Cross-Origin-Embedder-Policy require-corp;
            add_header Cross-Origin-Opener-Policy same-origin;
        }

        # API proxy to backend
        location /api/ {
            proxy_pass http://server:8080/api/;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            # WebSocket support (if needed)
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";

            # Timeouts
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;
        }

        # Health check endpoint
        location /health {
            return 200 'ok';
            add_header Content-Type text/plain;
        }
    }
}
```

---

## 7. Изменения в сборке

### 7.1 settings.gradle.kts

Закомментировать/удалить включение неиспользуемых модулей:

```kotlin
// services - ОТКЛЮЧИТЬ
// include(":services:notes")
// include(":services:news-crud")
// include(":services:notes-scheduler")
// include(":services:vectorizer")
// include(":services:rag")
// include(":services:github-webhook")

// mcp - ОТКЛЮЧИТЬ (кроме vdsina)
// include(":mcp:notes")
// include(":mcp:newsapi")
// include(":mcp:newscrud")
// include(":mcp:notes-polling")
// include(":mcp:rag")
// include(":mcp:git")
// include(":mcp:github-reviewer")
// include(":mcp:client")

// ДОБАВИТЬ
include(":mcp:vdsina")
```

### 7.2 server/src/main/kotlin/.../di/AppModule.kt

Удалить MCP-related DI bindings если есть.

---

## 8. .env.production

Создать файл `.env.production` в корне проекта (добавить в .gitignore):

```env
# Database
DATABASE_USER=aichallenge
DATABASE_PASSWORD=<strong-password>

# GigaChat API
GIGACHAT_CLIENT_ID=<your-client-id>
GIGACHAT_CLIENT_SECRET=<your-client-secret>

# OpenRouter API
OPENAI_API_KEY=<your-openrouter-key>
OPENAI_MODEL=openai/gpt-3.5-turbo

# VDSina (для локального MCP)
VDSINA_API_TOKEN=<your-vdsina-token>
VDSINA_SSH_KEY_ID=<your-ssh-key-id>
```

---

## 9. Обработка ошибок

### 9.1 VDSina API ошибки

MCP возвращает ошибки VDSina API как-есть в формате:

```json
{
  "error": true,
  "code": 400,
  "message": "Bad Request",
  "details": "Additional error description"
}
```

Коды ошибок:

- `400` - неверные параметры запроса
- `401` - невалидный токен
- `403` - недостаточно прав / превышен лимит запросов
- `404` - ресурс не найден
- `500` - внутренняя ошибка VDSina

### 9.2 Deploy ошибки

```json
{
  "error": true,
  "stage": "build|ssh|docker|copy|verify",
  "message": "Error description",
  "exit_code": 1
}
```

---

## 10. Тестирование

### 10.1 Ручное тестирование MCP

```bash
# Запуск MCP сервера
.\gradlew.bat :mcp:vdsina:run

# Проверка списка датацентров (curl)
curl -X POST http://localhost:8096/mcp \
  -H "Content-Type: application/json" \
  -d '{"tool": "list_datacenters", "params": {}}'
```

### 10.2 Полный цикл тестирования

1. Запустить MCP: `.\gradlew.bat :mcp:vdsina:run`
2. Через AI или вручную вызвать `create_server`
3. Дождаться статуса `active` через `get_server_status`
4. Вызвать `deploy_app`
5. Проверить доступность `http://<server-ip>`
6. Вызвать `delete_server` для cleanup

---

## 11. Риски и ограничения

### 11.1 Известные ограничения

1. **1GB RAM** может быть недостаточно для JVM + PostgreSQL при высокой нагрузке
2. **HTTP only** - нет шифрования трафика на VDS
3. **Первый deploy only** - скрипт не поддерживает обновление
4. **Нет автозапуска** - при ребуте VDS нужен ручной `docker compose up`

### 11.2 Риски

1. **Биллинг** - созданные серверы биллятся посуточно до удаления
2. **SSH ключ** - должен существовать в VDSina до создания сервера
3. **API токен** - не имеет срока действия, но меняется при смене пароля

---

## 12. Структура файлов после реализации

```
AiChallenge_One/
├── .env.production              # Секреты (в .gitignore)
├── .gitignore                   # Добавить .env.production
├── settings.gradle.kts          # Изменить: отключить модули
├── deploy/
│   ├── docker-compose.yml       # Production compose
│   └── nginx.conf               # Nginx конфигурация
├── scripts/
│   └── deploy-vdsina.ps1        # Deploy скрипт
├── mcp/
│   └── vdsina/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── kotlin/.../mcp_vdsina/
│           │   ├── Application.kt
│           │   ├── VdsinaMcpConfiguration.kt
│           │   ├── service/VdsinaApiService.kt
│           │   ├── model/VdsinaModels.kt
│           │   └── di/VdsinaModule.kt
│           └── resources/
│               ├── application.conf
│               ├── application-dev.conf
│               ├── logback.xml
│               └── keystore.jks (auto-generated)
└── documentation/tasks/vdsina/
    ├── vdsina-openapi.yaml      # API спецификация
    └── SPEC-MCP-VDSINA.md       # Этот документ
```

---

## 13. Чеклист реализации

- [ ] Создать модуль `mcp/vdsina` с build.gradle.kts
- [ ] Реализовать `VdsinaApiService` (HTTP клиент для VDSina API)
- [ ] Реализовать `VdsinaModels` (data classes для API)
- [ ] Реализовать MCP tools в `VdsinaMcpConfiguration`
- [ ] Создать `deploy/docker-compose.yml`
- [ ] Создать `deploy/nginx.conf`
- [ ] Создать `scripts/deploy-vdsina.ps1`
- [ ] Изменить `settings.gradle.kts` (отключить модули)
- [ ] Создать шаблон `.env.production.example`
- [ ] Добавить `.env.production` в `.gitignore`
- [ ] Обновить CLAUDE.md с информацией о mcp:vdsina
- [ ] Тестирование полного цикла

---

*Спецификация готова к реализации.*
