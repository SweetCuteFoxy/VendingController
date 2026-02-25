package com.vending.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import org.slf4j.LoggerFactory

class InventoryView {
    val root: BorderPane = BorderPane()
    private val logger = LoggerFactory.getLogger(InventoryView::class.java)

    init {
        root.styleClass.add("admin-view")
        buildStubContent()
    }

    private fun buildStubContent() {
        val stub = VBox(20.0).apply {
            alignment = Pos.CENTER
            padding = Insets(60.0)
            maxWidth = 560.0

            val iconLabel = Label("\uD83D\uDD0C").apply { font = Font.font(64.0) }

            val titleLabel = Label("Сервер учёта ТМЦ недоступен").apply {
                styleClass.add("page-title")
                font = Font.font(22.0)
            }

            val descLabel = Label(
                "Модуль учёта товарно-материальных ценностей временно отключён.\n" +
                "Сервер инвентаризации проходит плановое обновление и будет\n" +
                "доступен в ближайшее время. Приносим извинения за неудобства."
            ).apply {
                styleClass.add("page-subtitle")
                isWrapText = true
                style = "-fx-text-alignment: center; -fx-line-spacing: 4;"
            }

            val statusBox = HBox(8.0).apply {
                alignment = Pos.CENTER
                padding = Insets(16.0, 24.0, 16.0, 24.0)
                styleClass.add("server-status-box")
                children.addAll(
                    Label("\u25CF").apply { style = "-fx-text-fill: #ef6b6b; -fx-font-size: 14px;" },
                    Label("Статус: офлайн").apply { style = "-fx-text-fill: #ef6b6b; -fx-font-weight: bold;" },
                    Region().apply { prefWidth = 20.0 },
                    Label("Ожидаемое время восстановления: ~2 часа").apply {
                        style = "-fx-text-fill: #7a8299; -fx-font-size: 12px;"
                    }
                )
            }

            val retryBtn = Button("\u21BB Проверить подключение").apply {
                styleClass.add("primary-button")
                setOnAction {
                    text = "Проверка\u2026"
                    isDisable = true
                    Thread {
                        Thread.sleep(1500)
                        javafx.application.Platform.runLater {
                            text = "\u21BB Проверить подключение"
                            isDisable = false
                            val alert = Alert(Alert.AlertType.INFORMATION).apply {
                                title = "Проверка подключения"
                                headerText = "Сервер по-прежнему недоступен"
                                contentText = "Модуль учёта ТМЦ находится на обслуживании.\nПожалуйста, попробуйте позже."
                            }
                            alert.showAndWait()
                        }
                    }.start()
                }
            }

            children.addAll(iconLabel, titleLabel, descLabel, statusBox, retryBtn)
        }

        val toolbar = HBox(12.0).apply {
            styleClass.add("admin-toolbar")
            padding = Insets(12.0, 20.0, 12.0, 20.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Учёт ТМЦ").apply { font = Font.font(18.0); styleClass.add("page-title") }
            )
        }

        root.top = toolbar
        root.center = StackPane(stub).apply { alignment = Pos.CENTER }
    }
}