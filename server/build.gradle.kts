plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "ru.sber.cb.aichallenge_one"
version = "1.0.0"


application {
    mainClass.set("ru.sber.cb.aichallenge_one.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverConfig)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.clientLogging)
    //  implementation(libs.ktor.serverRouting)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.slf4j)
    implementation(libs.typesafe.config)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    //implementation("io.ktor:ktor-server-routing:3.3.1")
}

tasks.named<JavaExec>("run") {
    environment(System.getenv())
}

tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Run the application with application-dev.conf configuration"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ru.sber.cb.aichallenge_one.ApplicationKt")

    systemProperty("config.resource", "application-dev.conf")

    environment(System.getenv())
}

