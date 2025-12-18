plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "ru.sber.cb.aichallenge_one"
version = "1.0.0"

application {
    mainClass.set("ru.sber.cb.aichallenge_one.mcp_newsapi.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Shared models
    implementation(project(":shared"))

    // MCP SDK
    implementation(libs.mcp.sdk)

    // Ktor Server
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverSse)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.networkTls)

    // Ktor Client (for NewsAPI calls)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)

    // Typesafe Config (HOCON)
    implementation(libs.typesafe.config)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

tasks.named<JavaExec>("run") {
    environment(System.getenv())
    environment("SSL_ENABLED", true)
}



tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Run the application with application-dev.conf configuration"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ru.sber.cb.aichallenge_one.mcp_newsapi.ApplicationKt")

    environment("SSL_ENABLED", true)
    systemProperty("config.resource", "application-dev.conf")

    environment(System.getenv())
}
