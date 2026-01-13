plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "ru.sber.cb.aichallenge_one"
version = "1.0.0"

application {
    mainClass.set("ru.sber.cb.aichallenge_one.mcp_github_reviewer.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Shared models
    implementation(projects.shared)

    // MCP SDK
    implementation(libs.mcp.sdk)

    // GitHub API library
    implementation("org.kohsuke:github-api:1.321")

    // Ktor Server (для MCP endpoint)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverSse)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.networkTls)

    // Config
    implementation(libs.typesafe.config)

    // Логирование
    implementation(libs.logback)
}

tasks.named<JavaExec>("run") {
    environment(System.getenv())
    environment("SSL_ENABLED", true)
}

// Custom task для dev конфига
tasks.register<JavaExec>("runDev") {
    group = "application"
    mainClass.set(application.mainClass)
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("config.resource", "application-dev.conf")
    environment(System.getenv())
}
