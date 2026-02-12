package com.vending.service

import com.vending.dao.NotificationDAO
import com.vending.model.Notification
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.animation.FadeTransition
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.util.Duration
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

object NotificationService {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    private val pendingNotifications = ConcurrentLinkedQueue<Notification>()
    private val activeToasts = CopyOnWriteArrayList<VBox>()
    private var toastContainer: VBox? = null
    private var isProcessing = false

    // Display durations in milliseconds
    private const val CRITICAL_DURATION = 10000L
    private const val WARNING_DURATION = 7000L
    private const val INFO_DURATION = 5000L

    fun setToastContainer(container: VBox) {
        toastContainer = container
    }

    fun pushCritical(title: String, message: String, machineId: Int? = null) {
        push("critical", title, message, machineId)
    }

    fun pushWarning(title: String, message: String, machineId: Int? = null) {
        push("warning", title, message, machineId)
    }

    fun pushInfo(title: String, message: String, machineId: Int? = null) {
        push("info", title, message, machineId)
    }

    private fun push(type: String, title: String, message: String, machineId: Int?) {
        val notification = Notification(
            type = type, title = title, message = message,
            machineId = machineId, createdAt = LocalDateTime.now()
        )
        // Save to DB
        try {
            NotificationDAO.create(type, title, message, machineId)
        } catch (e: Exception) {
            logger.warn("Could not save notification to DB: ${e.message}")
        }
        pendingNotifications.add(notification)
        processQueue()
    }

    private fun processQueue() {
        if (isProcessing) return
        isProcessing = true

        Platform.runLater {
            while (pendingNotifications.isNotEmpty()) {
                val n = pendingNotifications.poll() ?: break
                showToast(n)
            }
            isProcessing = false
        }
    }

    private fun showToast(notification: Notification) {
        val container = toastContainer ?: return

        val toast = VBox(5.0).apply {
            val toastRef = this
            padding = Insets(12.0, 16.0, 12.0, 16.0)
            maxWidth = 380.0
            minWidth = 340.0
            alignment = Pos.CENTER_LEFT

            val bgColor = when (notification.type) {
                "critical" -> "#dc3545"
                "warning" -> "#fd7e14"
                "info" -> "#198754"
                else -> "#0d6efd"
            }
            val icon = when (notification.type) {
                "critical" -> "❌"
                "warning" -> "⚠️"
                "info" -> "ℹ️"
                else -> "📢"
            }

            style = """
                -fx-background-color: $bgColor;
                -fx-background-radius: 8;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 4);
            """.trimIndent()

            val header = HBox(8.0).apply {
                alignment = Pos.CENTER_LEFT
                children.addAll(
                    Label(icon).apply { style = "-fx-font-size: 16px;" },
                    Label(notification.title).apply {
                        style = "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;"
                        maxWidth = 260.0
                    }
                )
                val closeBtn = Button("✕").apply {
                    style = """
                        -fx-background-color: transparent; -fx-text-fill: white;
                        -fx-cursor: hand; -fx-font-size: 14px; -fx-padding: 0 4;
                    """.trimIndent()
                }
                val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
                children.addAll(spacer, closeBtn)
                closeBtn.setOnAction { removeToast(toastRef, container) }
            }

            val msgLabel = Label(notification.message).apply {
                style = "-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 12px;"
                isWrapText = true
                maxWidth = 340.0
            }

            children.addAll(header, msgLabel)

            if (notification.type == "critical") {
                val confirmBtn = Button("Понятно").apply {
                    style = """
                        -fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white;
                        -fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 12px;
                    """.trimIndent()
                }
                confirmBtn.setOnAction { removeToast(this, container) }
                children.add(confirmBtn)
            }

            opacity = 0.0
        }

        activeToasts.add(toast)
        container.children.add(0, toast)

        // Fade in
        FadeTransition(Duration.millis(300.0), toast).apply {
            fromValue = 0.0; toValue = 1.0; play()
        }

        // Auto-dismiss
        val duration = when (notification.type) {
            "critical" -> CRITICAL_DURATION
            "warning" -> WARNING_DURATION
            else -> INFO_DURATION
        }

        Timeline(KeyFrame(Duration.millis(duration.toDouble()), {
            removeToast(toast, container)
        })).play()
    }

    private fun removeToast(toast: VBox, container: VBox) {
        FadeTransition(Duration.millis(300.0), toast).apply {
            fromValue = 1.0; toValue = 0.0
            setOnFinished {
                container.children.remove(toast)
                activeToasts.remove(toast)
            }
            play()
        }
    }

    /** Simulates random notifications for TA monitoring */
    fun startSimulation() {
        val timeline = Timeline(KeyFrame(Duration.seconds(30.0), {
            val messages = listOf(
                Triple("warning", "Низкий запас", "Заканчивается товар в автомате. Осталось менее 5 шт."),
                Triple("info", "Успешная продажа", "Товар выдан. Оплата по карте."),
                Triple("critical", "Ошибка оборудования", "Нет сдачи в автомате. Требуется инкассация."),
                Triple("info", "Пополнение", "Автомат успешно пополнен товаром."),
                Triple("warning", "Необходимо ТО", "Приближается срок планового обслуживания."),
                Triple("critical", "Замятие товара", "Обнаружено замятие в лотке выдачи.")
            )
            val (type, title, msg) = messages.random()
            push(type, title, msg, null)
        }))
        timeline.cycleCount = Timeline.INDEFINITE
        timeline.play()
    }
}
