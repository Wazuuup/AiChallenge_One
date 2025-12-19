package ru.sber.cb.aichallenge_one.scheduler

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.typesafe.config.ConfigFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("NotesScheduler")

/**
 * Notes Scheduler Service
 * Periodically triggers notes summary endpoint based on cron expression
 */
fun main() {
    runBlocking {
        try {
            logger.info("Starting Notes Scheduler Service...")

            // Load configuration
            val config = ConfigFactory.load()
            val mcpServerUrl = config.getString("scheduler.mcp_server_url")
            val cronExpression = config.getString("scheduler.cron_expression")
            val enableScheduler = config.getBoolean("scheduler.enabled")

            logger.info("Configuration loaded:")
            logger.info("  MCP Server URL: $mcpServerUrl")
            logger.info("  Cron Expression: $cronExpression")
            logger.info("  Scheduler Enabled: $enableScheduler")

            if (!enableScheduler) {
                logger.warn("Scheduler is disabled in configuration. Exiting...")
                exitProcess(0)
            }

            // Create HTTP client
            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = true
                    })
                }
            }

            // Parse cron expression
            val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
            val parser = CronParser(cronDefinition)
            val cron = parser.parse(cronExpression)
            val executionTime = ExecutionTime.forCron(cron)

            logger.info("Scheduler started successfully. Waiting for next execution...")

            // Scheduling loop
            while (isActive) {
                try {
                    // Calculate next execution time
                    val now = ZonedDateTime.now()
                    val nextExecution = executionTime.nextExecution(now)

                    if (nextExecution.isPresent) {
                        val delay = java.time.Duration.between(now, nextExecution.get()).toMillis()
                        logger.info("Next execution scheduled at: ${nextExecution.get()}")
                        logger.info("Waiting ${delay}ms (${delay / 1000} seconds)...")

                        delay(delay)

                        // Trigger summary endpoint
                        logger.info("Triggering notes summary...")
                        val response = httpClient.post("$mcpServerUrl/trigger-summary")

                        if (response.status.value in 200..299) {
                            val responseBody = response.bodyAsText()
                            logger.info("Summary triggered successfully:")
                            logger.info(responseBody)
                        } else {
                            logger.error("Failed to trigger summary. HTTP Status: ${response.status}")
                        }
                    } else {
                        logger.error("No next execution time found. Exiting...")
                        break
                    }
                } catch (e: CancellationException) {
                    logger.info("Scheduler cancelled. Shutting down...")
                    break
                } catch (e: Exception) {
                    logger.error("Error during scheduling: ${e.message}", e)
                    // Wait 10 seconds before retrying
                    delay(10_000)
                }
            }

            httpClient.close()
            logger.info("Notes Scheduler Service stopped")
        } catch (e: Exception) {
            logger.error("Fatal error starting scheduler: ${e.message}", e)
            exitProcess(1)
        }
    }
}
