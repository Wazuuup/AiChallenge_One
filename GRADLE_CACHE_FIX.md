# Gradle Configuration Cache Fix

## Проблема

```
Could not find io.ktor:ktor-client-sse:3.3.3
```

## Причина

Gradle кеширует конфигурацию проекта. Даже после удаления зависимости из `build.gradle.kts`, старая конфигурация
остаётся в кеше.

## Решение

### Вариант 1: Автоматический (Рекомендуется)

Запустите созданный скрипт:

```bash
build-clean.bat
```

Скрипт:

1. Удалит конфигурационный кеш Gradle
2. Выполнит clean
3. Пересоберёт server модуль

### Вариант 2: Вручную

```bash
# 1. Удалить кеш
rmdir /s /q .gradle\configuration-cache

# 2. Clean проект
.\gradlew.bat clean --no-configuration-cache

# 3. Пересобрать
.\gradlew.bat :server:build --no-configuration-cache
```

### Вариант 3: Полная очистка (если Вариант 1 и 2 не помогли)

```bash
# 1. Удалить весь .gradle
rmdir /s /q .gradle

# 2. Удалить build директории
rmdir /s /q server\build
rmdir /s /q mcp-server\build
rmdir /s /q mcp-client\build

# 3. Пересобрать всё
.\gradlew.bat clean build --no-configuration-cache
```

## Проверка успешности

После сборки вы должны увидеть:

```
BUILD SUCCESSFUL in XXs
```

Если видите ошибки, проверьте:

### 1. Проверить libs.versions.toml

Откройте `gradle/libs.versions.toml` и убедитесь, что НЕТ строки:

```toml
ktor-clientSse = { module = "io.ktor:ktor-client-sse", version.ref = "ktor" }
```

Должно быть только:

```toml
ktor-serverSse = { module = "io.ktor:ktor-server-sse-jvm", version.ref = "ktor" }
```

### 2. Проверить server/build.gradle.kts

Убедитесь, что НЕТ строки:

```kotlin
implementation(libs.ktor.clientSse)
```

Должны быть только:

```kotlin
implementation(libs.ktor.clientCore)
implementation(libs.ktor.clientCio)
implementation(libs.ktor.clientContentNegotiation)
```

## Почему SSE работает без отдельной зависимости?

В **Ktor 3.x** SSE клиент включён в `ktor-client-core`.

```kotlin
// Это работает из коробки:
import io.ktor.client.plugins.sse.*  // ✅ Из ktor-client-core

val client = HttpClient(CIO) {
    install(SSE)  // ✅ Доступно
}
```

В **Ktor 2.x** требовалась отдельная зависимость:

```kotlin
implementation("io.ktor:ktor-client-sse:2.x.x")  // ❌ Только для Ktor 2
```

Но в **Ktor 3.x** это больше не нужно! ✅

## После успешной сборки

Запустите серверы:

```bash
# Terminal 1: MCP Server
.\gradlew.bat :mcp-server:run

# Terminal 2: Main Server
.\gradlew.bat :server:run
```

Затем тестируйте tool calling согласно `TOOL_CALLING_SETUP.md`

## Если всё ещё не работает

### Проверьте версию Gradle

```bash
.\gradlew.bat --version
```

Должна быть 8.x или выше.

### Проверьте версию Kotlin

В `gradle/libs.versions.toml`:

```toml
kotlin = "2.2.21"  # Должна быть 2.x
```

### Проверьте версию Ktor

```toml
ktor = "3.3.3"  # Должна быть 3.x
```

### Синхронизация IDE

Если используете IntelliJ IDEA:

1. File → Invalidate Caches... → Invalidate and Restart
2. После перезапуска: правой кнопкой на проект → Gradle → Reload Gradle Project

## Логи для диагностики

Если build всё ещё падает, запустите с подробными логами:

```bash
.\gradlew.bat :server:build --no-configuration-cache --stacktrace --info > build.log 2>&1
```

Затем проверьте `build.log` на наличие:

- `Could not find` - отсутствующие зависимости
- `Unresolved` - нерешённые конфликты
- `Duplicate` - дублирующиеся зависимости

## Частые вопросы

### Q: Почему не использовать просто `.\gradlew.bat build`?

A: Флаг `--no-configuration-cache` заставляет Gradle игнорировать кеш и пересоздать конфигурацию заново.

### Q: Как навсегда отключить configuration cache?

A: В `gradle.properties` добавьте:

```properties
org.gradle.configuration-cache=false
```

Но это замедлит сборку!

### Q: Безопасно ли удалять .gradle директорию?

A: Да! Gradle пересоздаст её при следующей сборке. Это просто кеш и временные файлы.

---

**Начните с:** `build-clean.bat`
