package com.vending.ui.admin

import com.vending.dao.*
import com.vending.model.ServiceOrder
import com.vending.model.Notification
import com.vending.util.ExportUtil
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ServiceOrdersView {
    private val logger = LoggerFactory.getLogger(ServiceOrdersView::class.java)
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
            valueProperty().addListener { _, _, _ -> filterOrders(this.value) }
        }

        val exportBtn = Button("📥 CSV").apply {
            styleClass.add("export-btn")
            setOnAction {
                val items = ordersData.toList()
                Thread {
                    ExportUtil.exportGenericCSV(
                        items,
                        listOf("№ заявки", "Автомат", "Тип", "Статус", "Приоритет", "Дата план", "Инженер", "Описание"),
                        { o -> listOf(
                            o.orderNumber, o.machineName,
                            when(o.type){"maintenance"->"Плановое ТО";"repair"->"Ремонт";"emergency"->"Аварийный";"inventory"->"Осмотр";else->o.type},
                            when(o.status){"new"->"Новая";"assigned"->"Назначена";"in_progress"->"В работе";"completed"->"Завершена";"cancelled"->"Отменена";else->o.status},
                            when(o.priority){"low"->"Низкий";"medium"->"Средний";"high"->"Высокий";"critical"->"Критический";else->o.priority},
                            o.scheduledDate.format(dFmt), o.engineerName.ifBlank{"-"}, o.description?:"-"
                        ) },
                        "service_orders.csv"
                    )
                }.start()
            }
        }

        val filterBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Статус:").apply { styleClass.add("filter-group-label") },
                statusCombo,
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                exportBtn, addBtn
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
                prefWidth = 160.0
                setCellFactory {
                    object : TableCell<ServiceOrder, Void>() {
                        private val viewBtn = Button("👁").apply { styleClass.add("action-btn") }
                        private val editBtn = Button("✏").apply { styleClass.add("action-btn") }
                        private val delBtn = Button("🗑").apply { styleClass.add("action-btn-danger") }
                        private val box = HBox(4.0, viewBtn, editBtn, delBtn)
                        init {
                            viewBtn.setOnAction {
                                val idx = index
                                if (idx in 0 until ordersTable.items.size) showOrderDetail(ordersTable.items[idx])
                            }
                            editBtn.setOnAction {
                                val idx = index
                                if (idx in 0 until ordersTable.items.size) showOrderDialog(ordersTable.items[idx])
                            }
                            delBtn.setOnAction {
                                val idx = index
                                if (idx in 0 until ordersTable.items.size) confirmDeleteOrder(ordersTable.items[idx])
                            }
                        }
                        override fun updateItem(item: Void?, empty: Boolean) {
                            super.updateItem(item, empty)
                            graphic = if (empty) null else box
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
                    } catch (e: Exception) {
                        logger.error("Failed to mark notifications as read", e)
                    }
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
        val isEdit = order != null
        val dialog = Dialog<ButtonType>().apply {
            title = if (isEdit) "Редактирование заявки" else "Новая заявка на обслуживание"
            headerText = if (isEdit) "Редактирование «${order!!.orderNumber}»" else "Создание заявки"
        }

        val machines = try { VendingMachineDAO.findAll() } catch (e: Exception) {
            logger.warn("Failed to load machines for dialog", e); emptyList()
        }
        val users = try { UserDAO.findAll() } catch (e: Exception) {
            logger.warn("Failed to load users for dialog", e); emptyList()
        }

        val machineCombo = ComboBox<String>().apply {
            machines.forEach { items.add("${it.id}: ${it.name}") }
            if (isEdit) {
                val match = items.find { it.startsWith("${order!!.machineId}:") }
                if (match != null) value = match else if (items.isNotEmpty()) value = items[0]
            } else {
                if (items.isNotEmpty()) value = items[0]
            }
            prefWidth = 250.0
        }

        val typeCombo = ComboBox<String>().apply {
            items.addAll("maintenance", "repair", "emergency", "inspection")
            value = if (isEdit) order!!.type else "maintenance"
        }

        val statusCombo = ComboBox<String>().apply {
            items.addAll("new", "assigned", "in_progress", "completed", "cancelled")
            value = if (isEdit) order!!.status else "new"
        }

        val priorityCombo = ComboBox<String>().apply {
            items.addAll("low", "medium", "high", "critical")
            value = if (isEdit) order!!.priority else "medium"
        }

        val datePicker = DatePicker(if (isEdit) order!!.scheduledDate else LocalDate.now().plusDays(1))

        val engineerCombo = ComboBox<String>().apply {
            items.add("— не назначен —")
            users.forEach { items.add("${it.id}: ${it.fullName}") }
            if (isEdit && order!!.engineerId != null) {
                val match = items.find { it.startsWith("${order.engineerId}:") }
                value = match ?: items[0]
            } else {
                value = items[0]
            }
            prefWidth = 250.0
        }

        val descField = TextArea().apply {
            promptText = "Описание работ…"
            prefRowCount = 3
            text = order?.description ?: ""
        }

        val orderNumField = TextField().apply {
            promptText = "Номер заявки (напр. SO-001)"
            text = if (isEdit) order!!.orderNumber else "SO-${System.currentTimeMillis() % 10000}"
            if (isEdit) isEditable = false
        }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; padding = Insets(16.0)
            var r = 0
            add(Label("№ заявки *"), 0, r); add(orderNumField, 1, r); r++
            add(Label("Автомат *"), 0, r); add(machineCombo, 1, r); r++
            add(Label("Тип"), 0, r); add(typeCombo, 1, r); r++
            if (isEdit) { add(Label("Статус"), 0, r); add(statusCombo, 1, r); r++ }
            add(Label("Приоритет"), 0, r); add(priorityCombo, 1, r); r++
            add(Label("Плановая дата"), 0, r); add(datePicker, 1, r); r++
            add(Label("Инженер"), 0, r); add(engineerCombo, 1, r); r++
            add(Label("Описание"), 0, r); add(descField, 1, r)
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

                        if (isEdit) {
                            ServiceDAO.updateOrder(
                                id = order!!.id,
                                type = typeCombo.value,
                                status = statusCombo.value,
                                priority = priorityCombo.value,
                                scheduledDate = datePicker.value,
                                engineerId = eId,
                                description = descField.text.trim().ifBlank { null }
                            )
                        } else {
                            ServiceDAO.createOrder(
                                orderNumber = orderNumField.text.trim(),
                                machineId = mId,
                                type = typeCombo.value,
                                priority = priorityCombo.value,
                                scheduledDate = datePicker.value,
                                engineerId = eId,
                                description = descField.text.trim().ifBlank { null }
                            )
                        }
                        Platform.runLater { loadOrders() }
                    } catch (e: Exception) {
                        Platform.runLater { Alert(Alert.AlertType.ERROR, "Ошибка: ${e.message}").showAndWait() }
                    }
                }.start()
            }
        }
    }

    private fun confirmDeleteOrder(order: ServiceOrder) {
        Alert(Alert.AlertType.CONFIRMATION, "Удалить заявку «${order.orderNumber}»?").showAndWait().ifPresent {
            if (it == ButtonType.OK) {
                Thread {
                    try {
                        ServiceDAO.deleteOrder(order.id)
                        Platform.runLater { loadOrders() }
                    } catch (e: Exception) {
                        logger.error("Failed to delete order", e)
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
                Platform.runLater {
                    ordersData.setAll(list)
                    if (list.isEmpty()) ordersTable.placeholder = Label("Нет сервисных заявок")
                }
            } catch (e: Exception) {
                logger.error("Failed to load service orders", e)
                Platform.runLater { ordersTable.placeholder = Label("Ошибка загрузки: ${e.message}") }
            }
        }.start()
    }

    private fun filterOrders(statusDisplay: String) {
        Thread {
            try {
                val all = ServiceDAO.findAllOrders()
                val statusCode = when (statusDisplay) {
                    "Новая" -> "new"
                    "Назначена" -> "assigned"
                    "В работе" -> "in_progress"
                    "Завершена" -> "completed"
                    "Отменена" -> "cancelled"
                    else -> null
                }
                val filtered = if (statusCode != null) all.filter { it.status == statusCode } else all
                Platform.runLater {
                    ordersData.setAll(filtered)
                    if (filtered.isEmpty()) ordersTable.placeholder = Label("Нет заявок по выбранному фильтру")
                }
            } catch (e: Exception) {
                logger.error("Failed to filter orders", e)
                Platform.runLater { ordersTable.placeholder = Label("Ошибка: ${e.message}") }
            }
        }.start()
    }

    private fun loadNotifications() {
        Thread {
            try {
                val list = NotificationDAO.findAll()
                Platform.runLater { notifsData.setAll(list) }
            } catch (e: Exception) {
                logger.error("Failed to load notifications", e)
            }
        }.start()
    }
}
