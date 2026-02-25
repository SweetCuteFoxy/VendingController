package com.vending.ui

import com.vending.dao.SaleDAO
import com.vending.dao.VendingMachineDAO
import javafx.animation.FadeTransition
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.util.Duration
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * Скрытая панель разработчика.
 * Вызывается по Ctrl+Shift+D.
 * Позволяет быстро добавлять продажи, менять статус автоматов и т.д.
 */
class DevPanel {
    private val logger = LoggerFactory.getLogger(DevPanel::class.java)
    private var overlay: StackPane? = null
    private var isVisible = false

    companion object {
        val HOTKEY: KeyCodeCombination = KeyCodeCombination(
            KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN
        )
    }

    fun toggle(parent: StackPane) {
        if (isVisible) {
            hide()
        } else {
            show(parent)
        }
    }

    private fun show(parent: StackPane) {
        val panel = buildPanel()
        overlay = StackPane().apply {
            styleClass.add("dev-overlay")
            children.add(panel)
            StackPane.setAlignment(panel, Pos.CENTER_RIGHT)
            setOnMouseClicked { e ->
                if (e.target == this) hide()
            }
        }
        parent.children.add(overlay)
        isVisible = true

        // Fade in
        FadeTransition(Duration.millis(200.0), overlay).apply {
            fromValue = 0.0; toValue = 1.0; play()
        }
    }

    fun hide() {
        val ov = overlay ?: return
        FadeTransition(Duration.millis(150.0), ov).apply {
            fromValue = 1.0; toValue = 0.0
            setOnFinished { (ov.parent as? Pane)?.children?.remove(ov) }
            play()
        }
        isVisible = false
        overlay = null
    }

    private fun buildPanel(): VBox {
        val statusLabel = Label().apply {
            styleClass.add("dev-status")
            isVisible = false
            isManaged = false
            isWrapText = true
        }

        fun showStatus(msg: String, success: Boolean) {
            statusLabel.text = msg
            statusLabel.style = if (success) "-fx-text-fill: #50cd89;" else "-fx-text-fill: #ef6b6b;"
            statusLabel.isVisible = true
            statusLabel.isManaged = true
        }

        // ---------- Quick Sale ----------
        val machineIdField = TextField().apply {
            promptText = "ID автомата"
            prefWidth = 100.0
            styleClass.add("dev-field")
        }
        val productIdField = TextField().apply {
            promptText = "ID продукта"
            prefWidth = 100.0
            styleClass.add("dev-field")
        }
        val qtyField = TextField().apply {
            promptText = "Кол-во"
            prefWidth = 70.0
            text = "1"
            styleClass.add("dev-field")
        }
        val priceField = TextField().apply {
            promptText = "Цена"
            prefWidth = 80.0
            text = "100"
            styleClass.add("dev-field")
        }
        val methodCombo = ComboBox<String>().apply {
            items.addAll("card", "cash", "qr")
            value = "card"
            prefWidth = 80.0
        }

        val addSaleBtn = Button("＋ Добавить продажу").apply {
            styleClass.addAll("primary-button", "dev-action-btn")
            setOnAction {
                val mId = machineIdField.text.trim().toIntOrNull()
                val pId = productIdField.text.trim().toIntOrNull()
                val qty = qtyField.text.trim().toIntOrNull() ?: 1
                val price = priceField.text.trim().toDoubleOrNull() ?: 100.0
                val method = methodCombo.value ?: "card"
                if (mId == null || pId == null) {
                    showStatus("⚠ Укажите ID автомата и продукта", false)
                    return@setOnAction
                }
                Thread {
                    try {
                        SaleDAO.create(mId, pId, qty, BigDecimal.valueOf(price), method)
                        Platform.runLater { showStatus("✓ Продажа добавлена (ТА=$mId, Прод=$pId)", true) }
                    } catch (e: Exception) {
                        Platform.runLater { showStatus("✗ ${e.message}", false) }
                    }
                }.start()
            }
        }

        val saleSection = VBox(8.0).apply {
            children.addAll(
                Label("⚡ Быстрая продажа").apply { styleClass.add("dev-section-title") },
                HBox(6.0).apply {
                    alignment = Pos.CENTER_LEFT
                    children.addAll(machineIdField, productIdField, qtyField, priceField, methodCombo)
                },
                addSaleBtn
            )
        }

        // ---------- Machine Status ----------
        val statusMachineIdField = TextField().apply {
            promptText = "ID автомата"
            prefWidth = 100.0
            styleClass.add("dev-field")
        }
        val statusCombo = ComboBox<String>().apply {
            items.addAll("working", "broken", "maintenance", "offline")
            value = "working"
            prefWidth = 140.0
        }
        val changeStatusBtn = Button("⟳ Изменить статус").apply {
            styleClass.addAll("primary-button", "dev-action-btn")
            setOnAction {
                val mId = statusMachineIdField.text.trim().toIntOrNull()
                if (mId == null) {
                    showStatus("⚠ Укажите ID автомата", false)
                    return@setOnAction
                }
                Thread {
                    try {
                        VendingMachineDAO.updateStatus(mId, statusCombo.value)
                        Platform.runLater { showStatus("✓ Статус ТА #$mId → ${statusCombo.value}", true) }
                    } catch (e: Exception) {
                        Platform.runLater { showStatus("✗ ${e.message}", false) }
                    }
                }.start()
            }
        }

        val machineSection = VBox(8.0).apply {
            children.addAll(
                Label("🔧 Статус автомата").apply { styleClass.add("dev-section-title") },
                HBox(6.0).apply {
                    alignment = Pos.CENTER_LEFT
                    children.addAll(statusMachineIdField, statusCombo)
                },
                changeStatusBtn
            )
        }

        // ---------- Theme Toggle ----------
        val themeToggle = CheckBox("☀ Светлая тема").apply {
            styleClass.add("dev-theme-toggle")
            isSelected = ThemeManager.isLight
            selectedProperty().addListener { _, _, newVal -> ThemeManager.isLight = newVal }
        }

        // ---------- Shortcuts Info ----------
        val infoBox = VBox(4.0).apply {
            children.addAll(
                Label("Горячие клавиши:").apply { styleClass.add("dev-section-title") },
                Label("Ctrl+Shift+D — открыть/закрыть эту панель").apply { styleClass.add("dev-hint") },
                Label("Ctrl+Shift+T — переключить тему").apply { styleClass.add("dev-hint") },
                Label("Esc — закрыть панель").apply { styleClass.add("dev-hint") }
            )
        }

        return VBox(18.0).apply {
            styleClass.add("dev-panel")
            padding = Insets(24.0)
            prefWidth = 440.0
            maxWidth = 440.0
            maxHeight = Double.MAX_VALUE
            children.addAll(
                HBox(10.0).apply {
                    alignment = Pos.CENTER_LEFT
                    children.addAll(
                        Label("🛠 Панель разработчика").apply {
                            font = Font.font(16.0)
                            styleClass.add("dev-panel-title")
                        },
                        Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                        Button("✕").apply {
                            styleClass.add("dev-close-btn")
                            setOnAction { hide() }
                        }
                    )
                },
                Separator(),
                themeToggle,
                Separator(),
                saleSection,
                Separator(),
                machineSection,
                Separator(),
                statusLabel,
                Region().apply { VBox.setVgrow(this, Priority.ALWAYS) },
                infoBox
            )
        }
    }
}
