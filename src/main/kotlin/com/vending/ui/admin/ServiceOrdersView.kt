package com.vending.ui.admin

import com.vending.dao.*
import com.vending.model.ServiceOrder
import com.vending.model.Notification
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ServiceOrdersView {
    val root: BorderPane = BorderPane()
    private val dFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val dtFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    // Service orders tab
    private val ordersTable = TableView<ServiceOrder>()
    private val ordersData = FXCollections.observableArrayList<ServiceOrder>()

    // Notifications tab
    private val notifsTable = TableView<Notification>()
    private val notifsData = FXCollections.observableArrayList<Notification>()

    init {
        root.styleClass.add("admin-view")
        buildContent()
        loadData()
    }

    private fun buildContent() {
        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
        }

        tabPane.tabs.addAll(
            Tab("Заявки на обслуживание", buildOrdersTab()),
            Tab("Уведомления", buildNotificationsTab())
        )

        val toolbar = HBox(10.0).apply {
            styleClass.add("admin-toolbar")
            padding = Insets(12.0, 20.0, 12.0, 20.0)
            alignment = Pos.CENTER_LEFT
            children.add(
                Label("Дополнительные настройки").apply { font = Font.font(18.0); styleClass.add("page-title") }
            )
        }

        root.top = toolbar
        root.center = tabPane
    }

    // ================== Service Orders Tab ==================

    private fun buildOrdersTab(): BorderPane {
        val pane = BorderPane()

        val addBtn = Button("+ Новая заявка").apply {
            styleClass.add("primary-button")
            setOnAction { showOrderDialog(null) }
        }

        val statusCombo = ComboBox<String>().apply {
            items.addAll("Все", "Новая", "Назначена", "В работе", "Завершена", "Отменена")
            value = "Все"
            prefWidth = 140.0
        }

        val filterBtn = Button("Фильтр").apply {
            styleClass.add("primary-button")
            setOnAction {
                Thread {
                    try {
                        val all = ServiceDAO.findAllOrders()
                        val statusCode = when (statusCombo.value) {
                            "Новая" -> "new"
                            "Назначена" -> "assigned"
                            "В работе" -> "in_progress"
                            "Завершена" -> "completed"
                            "Отменена" -> "cancelled"
                            else -> null
                        }
                        val filtered = if (statusCode != null) all.filter { it.status == statusCode } else all
                        Platform.runLater { ordersData.setAll(filtered) }
                    } catch (_: Exception) {}
                }.start()
            }
        }

        val filterBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Статус:").apply { styleClass.add("filter-group-label") },
                statusCombo, filterBtn,
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                addBtn
            )
        }

        ordersTable.items = ordersData
        ordersTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        ordersTable.styleClass.add("admin-table")
        ordersTable.placeholder = Label("Нет заявок")

        ordersTable.columns.addAll(
            TableColumn<ServiceOrder, String>("№").apply {
                setCellValueFactory { SimpleStringProperty(it.value.orderNumber) }; prefWidth = 90.0
            },
            TableColumn<ServiceOrder, String>("Автомат").apply {
                setCellValueFactory { SimpleStringProperty(it.value.machineName) }; prefWidth = 160.0
            },
            TableColumn<ServiceOrder, String>("Тип").apply {
                setCellValueFactory {
                    SimpleStringProperty(
                        when (it.value.type) {
                            "maintenance" -> "Плановое ТО"
                            "repair" -> "Ремонт"
                            "emergency" -> "Аварийный"
                            "inspection" -> "Осмотр"
                            else -> it.value.type
                        }
                    )
                }; prefWidth = 100.0
            },
            TableColumn<ServiceOrder, String>("Статус").apply {
                setCellValueFactory {
                    SimpleStringProperty(
                        when (it.value.status) {
                            "new" -> "Новая"
                            "assigned" -> "Назначена"
                            "in_progress" -> "В работе"
                            "completed" -> "Завершена"
                            "cancelled" -> "Отменена"
                            else -> it.value.status
                        }
                    )
                }; prefWidth = 100.0
                setCellFactory {
                    object : TableCell<ServiceOrder, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; graphic = null; style = "" }
                            else {
                                val badge = Label(item).apply {
                                    styleClass.add("status-badge")
                                    styleClass.add(
                                        when (item) {
                                            "Новая" -> "status-new"
                                            "Назначена" -> "status-assigned"
                                            "В работе" -> "status-progress"
                                            "Завершена" -> "status-completed"
                                            "Отменена" -> "status-cancelled"
                                            else -> "status-new"
                                        }
                                    )
                                }
                                graphic = badge; text = null
                            }
                        }
                    }
                }
            },
            TableColumn<ServiceOrder, String>("Приоритет").apply {
                setCellValueFactory {
                    SimpleStringProperty(
                        when (it.value.priority) {
                            "low" -> "Низкий"
                            "medium" -> "Средний"
                            "high" -> "Высокий"
                            "critical" -> "Критический"
                            else -> it.value.priority
                        }
                    )
                }; prefWidth = 90.0
                setCellFactory {
                    object : TableCell<ServiceOrder, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; style = "" }
                            else {
                                text = item
                                style = "-fx-text-fill: ${
                                    when (item) {
                                        "Критический" -> "#ef6b6b"
                                        "Высокий" -> "#f0b75a"
                                        "Средний" -> "#5b8def"
                                        else -> "#50cd89"
                                    }
                                }; -fx-font-weight: bold;"
                            }
                        }
                    }
                }
            },
            TableColumn<ServiceOrder, String>("Дата план").apply {
                setCellValueFactory { SimpleStringProperty(it.value.scheduledDate.format(dFmt)) }; prefWidth = 100.0
            },
            TableColumn<ServiceOrder, String>("Инженер").apply {
                setCellValueFactory { SimpleStringProperty(it.value.engineerName.ifBlank { "—" }) }; prefWidth = 120.0
            },
            TableColumn<ServiceOrder, String>("Описание").apply {
                setCellValueFactory { SimpleStringProperty(it.value.description ?: "—") }; prefWidth = 180.0
            },
            TableColumn<ServiceOrder, Void>("Действия").apply {
                prefWidth = 80.0
                setCellFactory {
                    object : TableCell<ServiceOrder, Void>() {
                        private val viewBtn = Button("👁").apply { styleClass.add("action-btn") }
                        init {
                            viewBtn.setOnAction {
                                val idx = index
                                if (idx >= 0 && idx < ordersTable.items.size) {
                                    showOrderDetail(ordersTable.items[idx])
                                }
                            }
                        }
                        override fun updateItem(item: Void?, empty: Boolean) {
                            super.updateItem(item, empty)
                            graphic = if (empty) null else viewBtn
                        }
                    }
                }
            }
        )

        pane.top = filterBar
        pane.center = ordersTable
        return pane
    }

    // ================== Notifications Tab ==================

    private fun buildNotificationsTab(): BorderPane {
        val pane = BorderPane()

        val addBtn = Button("+ Уведомление").apply {
            styleClass.add("primary-button")
            setOnAction { showNotifDialog() }
        }

        val markAllBtn = Button("Прочитать все").apply {
            styleClass.addAll("primary-button")
            setOnAction {
                Thread {
                    try {
                        notifsData.filter { !it.isRead }.forEach { NotificationDAO.markRead(it.id) }
                        Platform.runLater { loadNotifications() }
                    } catch (_: Exception) {}
                }.start()
            }
        }

        val filterBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Системные уведомления").apply { styleClass.add("filter-group-label") },
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                markAllBtn, addBtn
            )
        }

        notifsTable.items = notifsData
        notifsTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        notifsTable.styleClass.add("admin-table")
        notifsTable.placeholder = Label("Нет уведомлений")

        notifsTable.columns.addAll(
            TableColumn<Notification, String>("ID").apply {
                setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }; prefWidth = 50.0
            },
            TableColumn<Notification, String>("Тип").apply {
                setCellValueFactory {
                    SimpleStringProperty(
                        when (it.value.type) {
                            "critical" -> "Критическое"
                            "warning" -> "Предупреждение"
                            "info" -> "Информация"
                            else -> it.value.type
                        }
                    )
                }; prefWidth = 120.0
                setCellFactory {
                    object : TableCell<Notification, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; graphic = null }
                            else {
                                val badge = Label(item).apply {
                                    styleClass.add("status-badge")
                                    styleClass.add(
                                        when (item) {
                                            "Критическое" -> "status-cancelled"
                                            "Предупреждение" -> "status-assigned"
                                            else -> "status-completed"
                                        }
                                    )
                                }
                                graphic = badge; text = null
                            }
                        }
                    }
                }
            },
            TableColumn<Notification, String>("Заголовок").apply {
                setCellValueFactory { SimpleStringProperty(it.value.title) }; prefWidth = 200.0
            },
            TableColumn<Notification, String>("Сообщение").apply {
                setCellValueFactory { SimpleStringProperty(it.value.message) }; prefWidth = 300.0
            },
            TableColumn<Notification, String>("Прочитано").apply {
                setCellValueFactory { SimpleStringProperty(if (it.value.isRead) "Да" else "Нет") }; prefWidth = 80.0
                setCellFactory {
                    object : TableCell<Notification, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; style = "" }
                            else {
                                text = item
                                style = if (item == "Нет") "-fx-text-fill: #f0b75a; -fx-font-weight: bold;"
                                else "-fx-text-fill: #50cd89;"
                            }
                        }
                    }
                }
            },
            TableColumn<Notification, String>("Дата").apply {
                setCellValueFactory { SimpleStringProperty(it.value.createdAt?.format(dtFmt) ?: "—") }; prefWidth = 140.0
            }
        )

        pane.top = filterBar
        pane.center = notifsTable
        return pane
    }

    // ================== Dialogs ==================

    private fun showOrderDialog(order: ServiceOrder?) {
        val dialog = Dialog<ButtonType>().apply {
            title = "Новая заявка на обслуживание"
            headerText = "Создание заявки"
        }

        val machines = try { VendingMachineDAO.findAll() } catch (_: Exception) { emptyList() }
        val users = try { UserDAO.findAll() } catch (_: Exception) { emptyList() }

        val machineCombo = ComboBox<String>().apply {
            machines.forEach { items.add("${it.id}: ${it.name}") }
            if (items.isNotEmpty()) value = items[0]
            prefWidth = 250.0
        }

        val typeCombo = ComboBox<String>().apply {
            items.addAll("maintenance", "repair", "emergency", "inspection")
            value = "maintenance"
        }

        val priorityCombo = ComboBox<String>().apply {
            items.addAll("low", "medium", "high", "critical")
            value = "medium"
        }

        val datePicker = DatePicker(LocalDate.now().plusDays(1))

        val engineerCombo = ComboBox<String>().apply {
            items.add("— не назначен —")
            users.forEach { items.add("${it.id}: ${it.fullName}") }
            value = items[0]
            prefWidth = 250.0
        }

        val descField = TextArea().apply {
            promptText = "Описание работ…"
            prefRowCount = 3
        }

        val orderNumField = TextField().apply {
            promptText = "Номер заявки (напр. SO-001)"
            text = "SO-${System.currentTimeMillis() % 10000}"
        }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; padding = Insets(16.0)
            add(Label("№ заявки *"), 0, 0); add(orderNumField, 1, 0)
            add(Label("Автомат *"), 0, 1); add(machineCombo, 1, 1)
            add(Label("Тип"), 0, 2); add(typeCombo, 1, 2)
            add(Label("Приоритет"), 0, 3); add(priorityCombo, 1, 3)
            add(Label("Плановая дата"), 0, 4); add(datePicker, 1, 4)
            add(Label("Инженер"), 0, 5); add(engineerCombo, 1, 5)
            add(Label("Описание"), 0, 6); add(descField, 1, 6)
        }

        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.showAndWait().ifPresent { btn ->
            if (btn == ButtonType.OK && machineCombo.value != null) {
                Thread {
                    try {
                        val mId = machineCombo.value.substringBefore(":").trim().toInt()
                        val eId = if (engineerCombo.value.startsWith("—")) null
                        else engineerCombo.value.substringBefore(":").trim().toIntOrNull()

                        ServiceDAO.createOrder(
                            orderNumber = orderNumField.text.trim(),
                            machineId = mId,
                            type = typeCombo.value,
                            priority = priorityCombo.value,
                            scheduledDate = datePicker.value,
                            engineerId = eId,
                            description = descField.text.trim().ifBlank { null }
                        )
                        Platform.runLater { loadOrders() }
                    } catch (e: Exception) {
                        Platform.runLater { Alert(Alert.AlertType.ERROR, "Ошибка: ${e.message}").showAndWait() }
                    }
                }.start()
            }
        }
    }

    private fun showOrderDetail(order: ServiceOrder) {
        Alert(Alert.AlertType.INFORMATION).apply {
            title = "Заявка ${order.orderNumber}"
            headerText = "Детали заявки"
            contentText = buildString {
                appendLine("Номер: ${order.orderNumber}")
                appendLine("Автомат: ${order.machineName}")
                appendLine("Тип: ${order.type}")
                appendLine("Статус: ${order.status}")
                appendLine("Приоритет: ${order.priority}")
                appendLine("Дата план: ${order.scheduledDate.format(dFmt)}")
                appendLine("Инженер: ${order.engineerName.ifBlank { "не назначен" }}")
                appendLine("Описание: ${order.description ?: "—"}")
                if (order.problems != null) appendLine("Проблемы: ${order.problems}")
                if (order.actions != null) appendLine("Действия: ${order.actions}")
            }
        }.showAndWait()
    }

    private fun showNotifDialog() {
        val dialog = Dialog<ButtonType>().apply {
            title = "Новое уведомление"
            headerText = "Создание уведомления"
        }

        val typeCombo = ComboBox<String>().apply {
            items.addAll("info", "warning", "critical")
            value = "info"
        }
        val titleF = TextField().apply { promptText = "Заголовок" }
        val messageF = TextArea().apply { promptText = "Сообщение"; prefRowCount = 3 }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; padding = Insets(16.0)
            add(Label("Тип"), 0, 0); add(typeCombo, 1, 0)
            add(Label("Заголовок *"), 0, 1); add(titleF, 1, 1)
            add(Label("Сообщение *"), 0, 2); add(messageF, 1, 2)
        }

        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.showAndWait().ifPresent { btn ->
            if (btn == ButtonType.OK && titleF.text.isNotBlank()) {
                Thread {
                    try {
                        NotificationDAO.create(typeCombo.value, titleF.text.trim(), messageF.text.trim())
                        Platform.runLater { loadNotifications() }
                    } catch (e: Exception) {
                        Platform.runLater { Alert(Alert.AlertType.ERROR, "Ошибка: ${e.message}").showAndWait() }
                    }
                }.start()
            }
        }
    }

    // ================== Data Loading ==================

    private fun loadData() {
        loadOrders()
        loadNotifications()
    }

    private fun loadOrders() {
        Thread {
            try {
                val list = ServiceDAO.findAllOrders()
                Platform.runLater { ordersData.setAll(list) }
            } catch (_: Exception) {}
        }.start()
    }

    private fun loadNotifications() {
        Thread {
            try {
                val list = NotificationDAO.findAll()
                Platform.runLater { notifsData.setAll(list) }
            } catch (_: Exception) {}
        }.start()
    }
}
