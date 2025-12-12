package ru.sber.cb.aichallenge_one.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

data class MessageEntity(
    val role: String,
    val content: String,
    val isSummary: Boolean
)

class MessageRepository {
    private val logger = LoggerFactory.getLogger(MessageRepository::class.java)

    suspend fun saveMessage(
        provider: String,
        role: String,
        content: String,
        isSummary: Boolean = false
    ) {
        try {
            dbQuery {
                ConversationMessages.insert {
                    it[this.provider] = provider
                    it[this.role] = role
                    it[this.content] = content
                    it[this.isSummary] = isSummary
                }
            }
            logger.debug("Message saved: provider=$provider, role=$role, isSummary=$isSummary")
        } catch (e: Exception) {
            logger.error("Failed to save message", e)
            throw e
        }
    }

    suspend fun getHistory(provider: String): List<MessageEntity> {
        return try {
            dbQuery {
                ConversationMessages
                    .selectAll().where { ConversationMessages.provider eq provider }
                    .orderBy(ConversationMessages.createdAt:  to SortOrder.ASC)
                    .map { rowToMessage(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to get history for provider: $provider", e)
            emptyList()
        }
    }

    suspend fun clearHistory(provider: String) {
        try {
            dbQuery {
                ConversationMessages.deleteWhere {
                    ConversationMessages.provider eq provider
                }
            }
            logger.info("History cleared for provider: $provider")
        } catch (e: Exception) {
            logger.error("Failed to clear history for provider: $provider", e)
            throw e
        }
    }

    suspend fun replaceWithSummary(
        provider: String,
        summaryContent: String,
        summaryRole: String
    ) {
        try {
            dbQuery {
                // Delete all existing messages for this provider
                ConversationMessages.deleteWhere {
                    ConversationMessages.provider eq provider
                }

                // Insert the summary as a single message
                ConversationMessages.insert {
                    it[this.provider] = provider
                    it[this.role] = summaryRole
                    it[this.content] = summaryContent
                    it[this.isSummary] = true
                }
            }
            logger.info("History replaced with summary for provider: $provider")
        } catch (e: Exception) {
            logger.error("Failed to replace history with summary for provider: $provider", e)
            throw e
        }
    }

    private fun rowToMessage(row: ResultRow): MessageEntity {
        return MessageEntity(
            role = row[ConversationMessages.role],
            content = row[ConversationMessages.content],
            isSummary = row[ConversationMessages.isSummary]
        )
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
