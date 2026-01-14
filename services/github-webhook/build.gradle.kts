plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "ru.sber.cb.aichallenge_one"
version = "1.0.0"

application {
    mainClass.set("ru.sber.cb.aichallenge_one.github.webhook.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Shared models
    implementation(projects.shared)

    // Ktor Server
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serializationKotlinxJson)

    // Ktor Client (для RAG и OpenRouter API)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.clientLogging)

    // Koin DI
    implementation(libs.koin.ktor)

    // Config
    implementation(libs.typesafe.config)

    // Логирование
    implementation(libs.logback)

    // MCP SDK for tool calling
    implementation(libs.mcp.sdk)
}

tasks.named<JavaExec>("run") {
    environment(System.getenv())
}

// Custom task для dev конфига
tasks.register<JavaExec>("runDev") {
    group = "application"
    mainClass.set(application.mainClass)
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("config.resource", "application-dev.conf")
    environment(System.getenv())
}
