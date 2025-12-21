package ru.sber.cb.aichallenge_one.repository

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.Notification
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class NotificationRepository {
    private val logger = LoggerFactory.getLogger(NotificationRepository::class.java)

    private val unreadNotifications = ConcurrentLinkedQueue<Notification>()
    private val readNotifications = ConcurrentLinkedQueue<Notification>()

    companion object {
        private const val MAX_READ_HISTORY = 50
    }

    fun addNotification(text: String): Notification {
        val notification = Notification(
            id = UUID.randomUUID().toString(),
            text = text,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )

        unreadNotifications.add(notification)
        logger.debug("Added notification: ${notification.id} - ${text.take(50)}")

        return notification
    }

    fun getUnreadNotifications(): List<Notification> {
        return unreadNotifications.toList()
    }

    fun markAsRead(id: String): Boolean {
        val notification = unreadNotifications.find { it.id == id }

        if (notification != null) {
            unreadNotifications.remove(notification)

            val readNotification = notification.copy(isRead = true)
            readNotifications.add(readNotification)

            while (readNotifications.size > MAX_READ_HISTORY) {
                readNotifications.poll()
            }

            logger.debug("Marked notification as read: $id")
            return true
        }

        logger.warn("Notification not found: $id")
        return false
    }

    fun getAllNotifications(): List<Notification> {
        return (unreadNotifications.toList() + readNotifications.toList())
            .sortedByDescending { it.timestamp }
    }

    fun clearAll() {
        unreadNotifications.clear()
        readNotifications.clear()
        logger.info("Cleared all notifications")
    }

    fun getStats(): Map<String, Int> {
        return mapOf(
            "unread" to unreadNotifications.size,
            "read" to readNotifications.size,
            "total" to (unreadNotifications.size + readNotifications.size)
        )
    }
}
