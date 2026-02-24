package com.vending.ui

import com.vending.dao.SaleDAO
import com.vending.dao.ServiceDAO
import com.vending.model.Sale
import com.vending.model.ServiceOrder
import com.vending.model.ServiceHistoryEntry
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.stage.FileChooser
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

class ReportsView {
    private val logger = LoggerFactory.getLogger(ReportsView::class.java)
    val root: BorderPane = BorderPane()
    private val df = DecimalFormat("#,##0.00")
    private val dtFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val dFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Sales tab
    private val salesTable = TableView<Sale>()
    private val salesData = FXCollections.observableArrayList<Sale>()
    private var allSales = listOf<Sale>()

    // Service orders tab
    private val ordersTable = TableView<ServiceOrder>()
    private val ordersData = FXCollections.observableArrayList<ServiceOrder>()

    // Service history tab
    private val historyTable = TableView<ServiceHistoryEntry>()
    private val historyData = FXCollections.observableArrayList<ServiceHistoryEntry>()

    // Summary labels
    private val totalSalesLabel = Label("0")
    private val totalRevenueLabel = Label("0 ₽")

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
            Tab("Продажи", buildSalesTab()),
            Tab("Заявки на ТО", buildOrdersTab()),
            Tab("История обслуживания", buildHistoryTab())
        )

        // Title bar
        val toolbar = HBox(10.0).apply {
            styleClass.add("admin-toolbar")
            padding = Insets(12.0, 20.0, 12.0, 20.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Детальные отчёты").apply { font = Font.font(18.0); styleClass.add("page-title") },
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                HBox(6.0).apply {
                    alignment = Pos.CENTER
                    children.addAll(
                        Label("Всего продаж:").apply { styleClass.add("summary-label") },
                        totalSalesLabel.apply { styleClass.add("summary-value") }
                    )
                },
                HBox(6.0).apply {
                    alignment = Pos.CENTER
                    children.addAll(
                        Label("Выручка:").apply { styleClass.add("summary-label") },
                        totalRevenueLabel.apply { styleClass.add("summary-value") }
                    )
                },
                Button("↻ Обновить").apply {
                    styleClass.add("primary-button")
                    setOnAction { loadData() }
                }
            )
        }

        root.top = toolbar
        root.center = tabPane
    }

    // ================== Sales Tab ==================

    private fun buildSalesTab(): BorderPane {
        val pane = BorderPane()

        // Filters
        val searchField = TextField().apply {
            promptText = "Поиск по автомату / товару…"
            styleClass.add("filter-field")
            prefWidth = 260.0
        }

        val methodCombo = ComboBox<String>().apply {
            items.addAll("Все", "Наличные", "Карта")
            value = "Все"
            prefWidth = 140.0
        }

        val applySalesFilter = {
            val query = searchField.text.trim().lowercase()
            val method = when (methodCombo.value) {
                "Наличные" -> "cash"
                "Карта" -> "card"
                else -> null
            }
            val filtered = allSales.filter { sale ->
                val matchQuery = query.isEmpty() ||
                        sale.machineName.lowercase().contains(query) ||
                        sale.productName.lowercase().contains(query)
                val matchMethod = method == null || sale.paymentMethod == method
                matchQuery && matchMethod
            }
            salesData.setAll(filtered)
            updateSalesSummary(filtered)
        }

        searchField.textProperty().addListener { _, _, _ -> applySalesFilter() }
        methodCombo.valueProperty().addListener { _, _, _ -> applySalesFilter() }

        val exportBtn = Button("📥 CSV").apply {
            styleClass.add("primary-button")
            setOnAction { exportSalesCSV() }
        }

        val filterBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Фильтр:").apply { styleClass.add("filter-group-label") },
                searchField, methodCombo,
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                exportBtn
            )
        }

        // Table
        salesTable.items = salesData
        salesTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        salesTable.styleClass.add("admin-table")
        salesTable.placeholder = Label("Нет данных о продажах")

        salesTable.columns.addAll(
            TableColumn<Sale, String>("ID").apply {
                setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }; prefWidth = 50.0
            },
            TableColumn<Sale, String>("Автомат").apply {
                setCellValueFactory { SimpleStringProperty(it.value.machineName) }; prefWidth = 160.0
            },
            TableColumn<Sale, String>("Товар").apply {
                setCellValueFactory { SimpleStringProperty(it.value.productName) }; prefWidth = 160.0
            },
            TableColumn<Sale, String>("Кол-во").apply {
                setCellValueFactory { SimpleStringProperty(it.value.quantity.toString()) }; prefWidth = 70.0
            },
            TableColumn<Sale, String>("Цена").apply {
                setCellValueFactory { SimpleStringProperty("${df.format(it.value.unitPrice)} ₽") }; prefWidth = 100.0
            },
            TableColumn<Sale, String>("Сумма").apply {
                setCellValueFactory { SimpleStringProperty("${df.format(it.value.totalAmount)} ₽") }; prefWidth = 100.0
            },
            TableColumn<Sale, String>("Оплата").apply {
                setCellValueFactory {
                    SimpleStringProperty(
                        when (it.value.paymentMethod) {
                            "cash" -> "Наличные"
                            "card" -> "Карта"
                            else -> it.value.paymentMethod
                        }
                    )
                }; prefWidth = 90.0
                setCellFactory {
                    object : TableCell<Sale, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; style = "" }
                            else {
                                text = item
                                val color = when (item) {
                                    "Наличные" -> "#50cd89"
                                    "Карта" -> "#5b8def"
                                    else -> "#c8cdd8"
                                }
                                style = "-fx-text-fill: $color; -fx-font-weight: bold;"
                            }
                        }
                    }
                }
            },
            TableColumn<Sale, String>("Дата").apply {
                setCellValueFactory { SimpleStringProperty(it.value.saleTime.format(dtFmt)) }; prefWidth = 140.0
            }
        )

        pane.top = filterBar
        pane.center = salesTable
        return pane
    }

    // ================== Service Orders Tab ==================

    private fun buildOrdersTab(): BorderPane {
        val pane = BorderPane()

        val statusCombo = ComboBox<String>().apply {
            items.addAll("Все", "Новая", "Назначена", "В работе", "Завершена", "Отменена")
            value = "Все"
            prefWidth = 150.0
        }

        val priorityCombo = ComboBox<String>().apply {
            items.addAll("Все", "Низкий", "Средний", "Высокий", "Критический")
            value = "Все"
            prefWidth = 140.0
        }

        statusCombo.valueProperty().addListener { _, _, _ -> loadOrders(statusCombo.value, priorityCombo.value) }
        priorityCombo.valueProperty().addListener { _, _, _ -> loadOrders(statusCombo.value, priorityCombo.value) }

        val filterBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Статус:").apply { styleClass.add("filter-group-label") },
                statusCombo,
                Label("Приоритет:").apply { styleClass.add("filter-group-label") },
                priorityCombo
            )
        }

        ordersTable.items = ordersData
        ordersTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        ordersTable.styleClass.add("admin-table")
        ordersTable.placeholder = Label("Нет заявок на обслуживание")

        ordersTable.columns.addAll(
            TableColumn<ServiceOrder, String>("№ заявки").apply {
                setCellValueFactory { SimpleStringProperty(it.value.orderNumber) }; prefWidth = 100.0
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
                }; prefWidth = 110.0
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
                                    val badgeClass = when (item) {
                                        "Новая" -> "status-new"
                                        "Назначена" -> "status-assigned"
                                        "В работе" -> "status-progress"
                                        "Завершена" -> "status-completed"
                                        "Отменена" -> "status-cancelled"
                                        else -> "status-new"
                                    }
                                    styleClass.add(badgeClass)
                                }
                                graphic = badge
                                text = null
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
                }; prefWidth = 100.0
                setCellFactory {
                    object : TableCell<ServiceOrder, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; style = "" }
                            else {
                                text = item
                                val color = when (item) {
                                    "Критический" -> "#ef6b6b"
                                    "Высокий" -> "#f0b75a"
                                    "Средний" -> "#5b8def"
                                    "Низкий" -> "#50cd89"
                                    else -> "#c8cdd8"
                                }
                                style = "-fx-text-fill: $color; -fx-font-weight: bold;"
                            }
                        }
                    }
                }
            },
            TableColumn<ServiceOrder, String>("Дата план").apply {
                setCellValueFactory { SimpleStringProperty(it.value.scheduledDate.format(dFmt)) }; prefWidth = 100.0
            },
            TableColumn<ServiceOrder, String>("Инженер").apply {
                setCellValueFactory { SimpleStringProperty(it.value.engineerName.ifBlank { "—" }) }; prefWidth = 140.0
            },
            TableColumn<ServiceOrder, String>("Описание").apply {
                setCellValueFactory { SimpleStringProperty(it.value.description ?: "—") }; prefWidth = 200.0
            }
        )

        pane.top = filterBar
        pane.center = ordersTable
        return pane
    }

    // ================== Service History Tab ==================

    private fun buildHistoryTab(): BorderPane {
        val pane = BorderPane()

        historyTable.items = historyData
        historyTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        historyTable.styleClass.add("admin-table")
        historyTable.placeholder = Label("Нет истории обслуживания")

        historyTable.columns.addAll(
            TableColumn<ServiceHistoryEntry, String>("ID").apply {
                setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }; prefWidth = 50.0
            },
            TableColumn<ServiceHistoryEntry, String>("Автомат").apply {
                setCellValueFactory { SimpleStringProperty(it.value.machineName) }; prefWidth = 160.0
            },
            TableColumn<ServiceHistoryEntry, String>("Тип").apply {
                setCellValueFactory {
                    SimpleStringProperty(
                        when (it.value.eventType) {
                            "maintenance" -> "ТО"
                            "repair" -> "Ремонт"
                            "inspection" -> "Осмотр"
                            "installation" -> "Установка"
                            else -> it.value.eventType
                        }
                    )
                }; prefWidth = 90.0
            },
            TableColumn<ServiceHistoryEntry, String>("Дата").apply {
                setCellValueFactory { SimpleStringProperty(it.value.eventDate.format(dFmt)) }; prefWidth = 100.0
            },
            TableColumn<ServiceHistoryEntry, String>("Описание").apply {
                setCellValueFactory { SimpleStringProperty(it.value.description) }; prefWidth = 200.0
            },
            TableColumn<ServiceHistoryEntry, String>("Инженер").apply {
                setCellValueFactory { SimpleStringProperty(it.value.engineerName.ifBlank { "—" }) }; prefWidth = 140.0
            },
            TableColumn<ServiceHistoryEntry, String>("Длительность (ч)").apply {
                setCellValueFactory { SimpleStringProperty(it.value.duration?.toString() ?: "—") }; prefWidth = 100.0
            },
            TableColumn<ServiceHistoryEntry, String>("Стоимость").apply {
                setCellValueFactory { SimpleStringProperty(if (it.value.cost != null) "${df.format(it.value.cost)} ₽" else "—") }; prefWidth = 100.0
            }
        )

        val infoBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.add(Label("Полная история сервисных событий").apply { styleClass.add("filter-group-label") })
        }

        pane.top = infoBar
        pane.center = historyTable
        return pane
    }

    // ================== Data Loading ==================

    private fun loadData() {
        Thread {
            try {
                allSales = SaleDAO.findAll()
                val orders = ServiceDAO.findAllOrders()
                val history = ServiceDAO.findHistory()
                Platform.runLater {
                    salesData.setAll(allSales)
                    ordersData.setAll(orders)
                    historyData.setAll(history)
                    updateSalesSummary(allSales)
                }
            } catch (e: Exception) {
                Platform.runLater {
                    salesTable.placeholder = Label("Ошибка загрузки: ${e.message}")
                }
            }
        }.start()
    }

    private fun loadOrders(statusFilter: String, priorityFilter: String) {
        Thread {
            try {
                var orders = ServiceDAO.findAllOrders()
                if (statusFilter != "Все") {
                    val statusCode = when (statusFilter) {
                        "Новая" -> "new"
                        "Назначена" -> "assigned"
                        "В работе" -> "in_progress"
                        "Завершена" -> "completed"
                        "Отменена" -> "cancelled"
                        else -> null
                    }
                    if (statusCode != null) orders = orders.filter { it.status == statusCode }
                }
                if (priorityFilter != "Все") {
                    val priCode = when (priorityFilter) {
                        "Низкий" -> "low"
                        "Средний" -> "medium"
                        "Высокий" -> "high"
                        "Критический" -> "critical"
                        else -> null
                    }
                    if (priCode != null) orders = orders.filter { it.priority == priCode }
                }
                Platform.runLater {
                    ordersData.setAll(orders)
                    if (orders.isEmpty()) ordersTable.placeholder = Label("Нет заявок по выбранным фильтрам")
                }
            } catch (e: Exception) {
                logger.error("Failed to load service orders", e)
                Platform.runLater { ordersTable.placeholder = Label("Ошибка загрузки: ${e.message}") }
            }
        }.start()
    }

    private fun updateSalesSummary(sales: List<Sale>) {
        totalSalesLabel.text = sales.size.toString()
        totalRevenueLabel.text = "${df.format(sales.sumOf { it.totalAmount })} ₽"
    }

    private fun exportSalesCSV() {
        val data = salesData.toList()
        if (data.isEmpty()) {
            Alert(Alert.AlertType.INFORMATION, "Нет данных для экспорта").showAndWait()
            return
        }
        val chooser = FileChooser().apply {
            title = "Сохранить отчёт о продажах"
            extensionFilters.add(FileChooser.ExtensionFilter("CSV", "*.csv"))
            initialFileName = "sales_report.csv"
        }
        val file = chooser.showSaveDialog(root.scene?.window) ?: return
        try {
            file.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write("\uFEFF") // BOM for Excel
                w.write("ID;Автомат;Товар;Кол-во;Цена;Сумма;Оплата;Дата\n")
                data.forEach { s ->
                    val pay = if (s.paymentMethod == "cash") "Наличные" else "Карта"
                    w.write("${s.id};${s.machineName};${s.productName};${s.quantity};${s.unitPrice};${s.totalAmount};$pay;${s.saleTime.format(dtFmt)}\n")
                }
            }
            Alert(Alert.AlertType.INFORMATION, "Экспортировано ${data.size} записей в ${file.name}").showAndWait()
        } catch (e: Exception) {
            logger.error("Failed to export sales CSV", e)
            Alert(Alert.AlertType.ERROR, "Ошибка экспорта: ${e.message}").showAndWait()
        }
    }
}
