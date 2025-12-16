# Решение проблемы SSL сертификата

## Проблема

При запуске `ExchangeRateClientSSL` возникает ошибка:

```
io.ktor.network.tls.TLSException: No server host: v573465.hosted-by-vdsina.com in the server certificate.
Provided in certificate: 127.0.0.1, localhost, 0.0.0.0
```

**Причина:** SSL сертификат был создан только для localhost, но сервер находится на `89.124.67.120` (hostname:
`v573465.hosted-by-vdsina.com`).

## Решение

Код уже обновлен для включения правильных доменов в сертификат. Выполните следующие шаги:

### Шаг 1: Удалить старый keystore (локально)

```bash
regenerate-keystore.bat
```

Или вручную:

```bash
del mcp-server\src\main\resources\keystore.jks
```

### Шаг 2: Собрать и запустить сервер локально (для тестирования)

```bash
.\gradlew.bat :mcp-server:run
```

При первом запуске будет создан новый keystore с правильными доменами:

- `127.0.0.1`
- `localhost`
- `0.0.0.0`
- `89.124.67.120` ← новый
- `v573465.hosted-by-vdsina.com` ← новый

### Шаг 3: Развернуть на удаленный сервер

```bash
deploy-mcp-server-simple.bat
```

Скрипт развертывания автоматически:

1. Удалит старый keystore на сервере
2. Развернет обновленный код
3. При запуске сервер создаст новый keystore с правильными доменами

### Шаг 4: Проверить работу клиента

```bash
.\gradlew.bat :mcp-client:runExchangeRateSSL
```

Клиент должен успешно подключиться к серверу через HTTPS.

## Что было исправлено

### 1. Application.kt (mcp-server)

Обновлена функция генерации сертификата:

```kotlin
domains = listOf(
    "127.0.0.1",
    "localhost",
    "0.0.0.0",
    "89.124.67.120",  // Production IP
    "v573465.hosted-by-vdsina.com"  // Production hostname
)
```

### 2. ExchangeRateClientSSL.kt (mcp-client)

Исправлена конфигурация SSL:

```kotlin
https {
    serverName = "89.124.67.120"  // Use IP to match certificate
}
```

### 3. deploy-remote.sh

Добавлено удаление старого keystore при развертывании:

```bash
rm -f src/main/resources/keystore.jks
```

## Проверка сертификата

Чтобы проверить домены в сертификате:

```bash
# После запуска сервера
keytool -list -v -keystore mcp-server\src\main\resources\keystore.jks -storepass changeit
```

Найдите секцию `SubjectAlternativeName` - там должны быть все домены.

## Альтернативное решение (только для разработки)

Если вы хотите полностью отключить проверку hostname (НЕБЕЗОПАСНО!):

В `ExchangeRateClientSSL.kt` измените:

```kotlin
https {
    serverName = null  // Полностью отключить проверку
}
```

**⚠️ ВНИМАНИЕ:** Это делает соединение уязвимым к MITM атакам. Используйте только в разработке!

## Для production

В production окружении:

1. Используйте настоящий SSL сертификат от Certificate Authority (например, Let's Encrypt)
2. Удалите `trustManager` который принимает все сертификаты
3. Настройте правильную валидацию сертификатов
4. Используйте HTTPS для всех соединений

См. `SSL_README.md` для деталей.
