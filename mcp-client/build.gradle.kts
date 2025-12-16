plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "ru.sber.cb.aichallenge_one"
version = "1.0.0"

application {
    mainClass.set("ru.sber.cb.aichallenge_one.mcp_client.ApplicationKt")
}

dependencies {
    // MCP SDK
    implementation(libs.mcp.sdk)

    // Ktor Client
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serializationJson)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(libs.kotlin.testJunit)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// Task to run the ExchangeRateClient
tasks.register<JavaExec>("runExchangeRate") {
    group = "application"
    description = "Run the Exchange Rate Client (calls get_exchange_rate tool with USD)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ru.sber.cb.aichallenge_one.mcp_client.ExchangeRateClientKt")
    standardInput = System.`in`
}
