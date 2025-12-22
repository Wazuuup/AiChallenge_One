plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "ru.sber.cb.aichallenge_one"
version = "1.0.0"

application {
    mainClass.set("ru.sber.cb.aichallenge_one.vectorizer.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Shared module dependency
    implementation(projects.shared)

    // Ktor Server
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverConfig)
    implementation(libs.ktor.serializationKotlinxJson)

    // Ktor Client (for Ollama API)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.clientLogging)

    // Dependency Injection
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.slf4j)

    // Configuration
    implementation(libs.typesafe.config)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javatime)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.pgvector)

    // Tokenization
    implementation(libs.jtokkit)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

tasks.named<JavaExec>("run") {
    environment(System.getenv())
}
