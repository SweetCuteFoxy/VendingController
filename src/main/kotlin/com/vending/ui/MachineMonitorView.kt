package com.vending.ui

import com.vending.dao.VendingMachineDAO
import com.vending.model.VendingMachine
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import java.text.DecimalFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class MachineMonitorView {
    val root: BorderPane = BorderPane()
    private val table = TableView<MonitorRow>()
    private val data = FXCollections.observableArrayList<MonitorRow>()
    private var allMachines = listOf<VendingMachine>()
    private val df = DecimalFormat("#,##0.00")

    // Filters
    private var statusFilter: String? = null // working, broken, maintenance
    private var connectionFilter: String? = null // wifi, gsm, all
    private var additionalFilter: String? = null

    // Summary labels
    private val totalMachinesLabel = Label("0")
    private val totalMoneyLabel = Label("0 ₽")

    init {
        root.styleClass.add("admin-view")
        buildFilters()
        buildTable()
        buildSummary()
        loadData()
    }

    private fun buildFilters() {
        val filterBox = VBox(8.0).apply {
            styleClass.add("monitor-filter-box")
            padding = Insets(12.0, 16.0, 12.0, 16.0)
        }

        val titleRow = HBox(10.0).apply {
            alignment = Pos.CENTER_LEFT
            children.add(Label("Монитор торговых автоматов").apply {
                font = Font.font(18.0); styleClass.add("page-title")
            })
        }

        // Status filter
        val statusLabel = Label("Общее состояние:")
        val statusGroup = HBox(8.0).apply { alignment = Pos.CENTER_LEFT }
        val allStatusBtn = ToggleButton("Все").apply { styleClass.add("filter-toggle"); isSelected = true }
        val workingBtn = ToggleButton("🟢 Работает").apply { styleClass.add("filter-toggle-green") }
        val brokenBtn = ToggleButton("🔴 Не работает").apply { styleClass.add("filter-toggle-red") }
        val maintBtn = ToggleButton("🔵 На обслуживании").apply { styleClass.add("filter-toggle-blue") }
        val statusToggle = ToggleGroup()
        listOf(allStatusBtn, workingBtn, brokenBtn, maintBtn).forEach { it.toggleGroup = statusToggle }
        statusGroup.children.addAll(statusLabel, allStatusBtn, workingBtn, brokenBtn, maintBtn)

        // Connection filter
        val connLabel = Label("Тип подключения:")
        val connGroup = HBox(8.0).apply { alignment = Pos.CENTER_LEFT }
        val allConnBtn = ToggleButton("Все").apply { styleClass.add("filter-toggle"); isSelected = true }
        val wifiBtn = ToggleButton("WiFi").apply { styleClass.add("filter-toggle") }
        val gsmBtn = ToggleButton("GSM").apply { styleClass.add("filter-toggle") }
        val connToggle = ToggleGroup()
        listOf(allConnBtn, wifiBtn, gsmBtn).forEach { it.toggleGroup = connToggle }
        connGroup.children.addAll(connLabel, allConnBtn, wifiBtn, gsmBtn)

        // Additional status filter
        val addLabel = Label("Доп. статусы:")
        val addGroup = HBox(8.0).apply { alignment = Pos.CENTER_LEFT }
        val allAddBtn = ToggleButton("Все").apply { styleClass.add("filter-toggle"); isSelected = true }
        val lowStockBtn = ToggleButton("Мало товара").apply { styleClass.add("filter-toggle") }
        val needServiceBtn = ToggleButton("Нужно ТО").apply { styleClass.add("filter-toggle") }
        val addToggle = ToggleGroup()
        listOf(allAddBtn, lowStockBtn, needServiceBtn).forEach { it.toggleGroup = addToggle }
        addGroup.children.addAll(addLabel, allAddBtn, lowStockBtn, needServiceBtn)

        val applyBtn = Button("Применить").apply {
            styleClass.add("primary-button")
            setOnAction {
                statusFilter = when (statusToggle.selectedToggle) {
                    workingBtn -> "working"
                    brokenBtn -> "broken"
                    maintBtn -> "maintenance"
                    else -> null
                }
                connectionFilter = when (connToggle.selectedToggle) {
                    wifiBtn -> "wifi"
                    gsmBtn -> "gsm"
                    else -> null
                }
                additionalFilter = when (addToggle.selectedToggle) {
                    lowStockBtn -> "low_stock"
                    needServiceBtn -> "need_service"
                    else -> null
                }
                applyFilters()
            }
        }

        val filtersRow = HBox(16.0).apply {
            alignment = Pos.CENTER_LEFT
            children.addAll(statusGroup, connGroup, addGroup, applyBtn)
            isWrapText(this)
        }

        filterBox.children.addAll(titleRow, filtersRow)
        root.top = filterBox
    }

    private fun isWrapText(hbox: HBox) {
        // Allow wrapping by making the container a FlowPane-like behavior
    }

    private fun buildSummary() {
        val summaryBar = HBox(20.0).apply {
            styleClass.add("monitor-summary")
            padding = Insets(8.0, 16.0, 8.0, 16.0)
            alignment = Pos.CENTER_LEFT
        }
        summaryBar.children.addAll(
            HBox(6.0).apply {
                children.addAll(
                    Label("Итого автоматов:").apply { styleClass.add("summary-label") },
                    totalMachinesLabel.apply { styleClass.add("summary-value") }
                )
            },
            HBox(6.0).apply {
                children.addAll(
                    Label("Денег в автоматах:").apply { styleClass.add("summary-label") },
                    totalMoneyLabel.apply { styleClass.add("summary-value") }
                )
            }
        )
        root.bottom = summaryBar
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildTable() {
        table.items = data
        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        table.styleClass.add("monitor-table")

        val numCol = TableColumn<MonitorRow, String>("№").apply {
            setCellValueFactory { SimpleStringProperty(it.value.index.toString()) }; prefWidth = 40.0
        }
        val nameCol = TableColumn<MonitorRow, String>("ТП").apply {
            setCellValueFactory { SimpleStringProperty(it.value.name) }; prefWidth = 160.0
        }
        val connectionCol = TableColumn<MonitorRow, String>("Связь").apply {
            setCellValueFactory { SimpleStringProperty(it.value.connection) }; prefWidth = 80.0
            setCellFactory {
                object : TableCell<MonitorRow, String>() {
                    override fun updateItem(item: String?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (item == null || empty) { text = null; graphic = null }
                        else {
                            val color = when (item) {
                                "Online" -> "#50cd89"
                                "Offline" -> "#f1416c"
                                else -> "#ffc700"
                            }
                            text = item
                            style = "-fx-text-fill: $color; -fx-font-weight: bold;"
                        }
                    }
                }
            }
        }
        val loadCol = TableColumn<MonitorRow, String>("Загрузка").apply {
            setCellValueFactory { SimpleStringProperty(it.value.loadInfo) }; prefWidth = 120.0
            setCellFactory {
                object : TableCell<MonitorRow, String>() {
                    override fun updateItem(item: String?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (item == null || empty) { text = null; graphic = null }
                        else {
                            val pct = item.replace("%", "").toDoubleOrNull() ?: 50.0
                            val bar = ProgressBar(pct / 100.0).apply {
                                prefWidth = 80.0; prefHeight = 14.0
                                val barColor = when {
                                    pct >= 70 -> "#50cd89"
                                    pct >= 30 -> "#ffc700"
                                    else -> "#f1416c"
                                }
                                style = "-fx-accent: $barColor;"
                            }
                            val label = Label("${pct.toInt()}%").apply { font = Font.font(10.0) }
                            graphic = HBox(4.0, bar, label).apply { alignment = Pos.CENTER_LEFT }
                            text = null
                        }
                    }
                }
            }
        }
        val moneyCol = TableColumn<MonitorRow, String>("Денежные средства").apply {
            setCellValueFactory { SimpleStringProperty(it.value.money) }; prefWidth = 120.0
        }
        val eventsCol = TableColumn<MonitorRow, String>("События").apply {
            setCellValueFactory { SimpleStringProperty(it.value.events) }; prefWidth = 140.0
        }
        val equipCol = TableColumn<MonitorRow, String>("Оборудование").apply {
            setCellValueFactory { SimpleStringProperty(it.value.equipment) }; prefWidth = 130.0
            setCellFactory {
                object : TableCell<MonitorRow, String>() {
                    override fun updateItem(item: String?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (item == null || empty) { text = null; style = "" }
                        else { text = item; style = "-fx-text-fill: #50cd89;" }
                    }
                }
            }
        }
        val infoCol = TableColumn<MonitorRow, String>("Информация").apply {
            setCellValueFactory { SimpleStringProperty(it.value.info) }; prefWidth = 180.0
        }
        val extraCol = TableColumn<MonitorRow, String>("Доп.").apply {
            setCellValueFactory { SimpleStringProperty(it.value.extra) }; prefWidth = 100.0
        }

        // Status indicator on first column
        val statusCol = TableColumn<MonitorRow, String>("⬤").apply {
            setCellValueFactory { SimpleStringProperty(it.value.status) }; prefWidth = 35.0; maxWidth = 40.0
            setCellFactory {
                object : TableCell<MonitorRow, String>() {
                    override fun updateItem(item: String?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (item == null || empty) { text = null; style = "" }
                        else {
                            text = "●"
                            val color = when (item) {
                                "working" -> "#50cd89"
                                "broken" -> "#f1416c"
                                "maintenance" -> "#3699ff"
                                else -> "#7239ea"
                            }
                            style = "-fx-text-fill: $color; -fx-font-size: 16;"
                        }
                    }
                }
            }
        }

        table.columns.addAll(statusCol, numCol, nameCol, connectionCol, loadCol, moneyCol, eventsCol, equipCol, infoCol, extraCol)
        root.center = table
    }

    private fun loadData() {
        Thread {
            try {
                allMachines = VendingMachineDAO.findAll()
                Platform.runLater { applyFilters() }
            } catch (e: Exception) {
                Platform.runLater {
                    data.clear()
                    totalMachinesLabel.text = "Ошибка загрузки"
                }
            }
        }.start()
    }

    private fun applyFilters() {
        var filtered = allMachines.toList()

        // Status filter
        if (statusFilter != null) {
            filtered = filtered.filter { it.status == statusFilter }
        }

        // Connection filter (simulated)
        if (connectionFilter != null) {
            filtered = filtered.filter { vm ->
                val simConn = simulateConnectionType(vm.id)
                simConn == connectionFilter
            }
        }

        // Additional filter
        if (additionalFilter != null) {
            filtered = when (additionalFilter) {
                "low_stock" -> filtered.filter { it.resourceUsagePercent > 80 }
                "need_service" -> filtered.filter {
                    it.nextServiceDate != null && it.nextServiceDate.isBefore(java.time.LocalDate.now().plusDays(7))
                }
                else -> filtered
            }
        }

        if (filtered.isEmpty()) {
            data.clear()
            totalMachinesLabel.text = "0"
            totalMoneyLabel.text = "0 ₽"

            // Show "no results" message
            val emptyBox = VBox(16.0).apply {
                alignment = Pos.CENTER
                padding = Insets(40.0)
                children.addAll(
                    Label("🔍").apply { font = Font.font(48.0) },
                    Label("Нет автоматов, удовлетворяющих условиям фильтра").apply {
                        font = Font.font(14.0); opacity = 0.6
                    }
                )
            }
            root.center = StackPane(table, emptyBox)
            return
        }

        root.center = table

        val rows = filtered.mapIndexed { idx, vm -> buildMonitorRow(idx + 1, vm) }
        data.setAll(rows)

        totalMachinesLabel.text = filtered.size.toString()
        totalMoneyLabel.text = "${df.format(filtered.sumOf { it.currentCash })} ₽"
    }

    private fun buildMonitorRow(index: Int, vm: VendingMachine): MonitorRow {
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
        val connStatus = simulateConnectionStatus(vm.id)
        val connType = simulateConnectionType(vm.id)
        val loadPct = simulateLoad(vm.id)
        val cashInfo = "${df.format(vm.currentCash)} ₽"
        val events = simulateEvents(vm.id)
        val equipment = buildEquipmentString(vm)
        val info = buildInfoString(vm, connType)
        val extra = if (vm.nextServiceDate != null && vm.nextServiceDate.isBefore(java.time.LocalDate.now().plusDays(5)))
            "⚠ Скоро ТО" else "—"

        return MonitorRow(
            index = index,
            name = vm.name,
            status = vm.status,
            connection = connStatus,
            loadInfo = "${loadPct}%",
            money = cashInfo,
            events = events,
            equipment = equipment,
            info = info,
            extra = extra,
            machine = vm
        )
    }

    // Simulated API data for monitoring
    private fun simulateConnectionStatus(machineId: Int): String {
        val hash = machineId * 31 + 17
        return if (hash % 10 < 8) "Online" else "Offline"
    }

    private fun simulateConnectionType(machineId: Int): String {
        return if (machineId % 3 == 0) "wifi" else "gsm"
    }

    private fun simulateLoad(machineId: Int): Int {
        return 30 + (machineId * 7 + 13) % 65
    }

    private fun simulateEvents(machineId: Int): String {
        val events = listOf("Без происшествий", "Продажа", "Инкассация", "Низкий запас", "Ошибка оплаты")
        return events[(machineId + LocalTime.now().second) % events.size]
    }

    private fun buildEquipmentString(vm: VendingMachine): String {
        val parts = mutableListOf<String>()
        if (vm.type == "cash" || vm.type == "both") parts.add("Купюроприёмник")
        if (vm.type == "card" || vm.type == "both") parts.add("Картоприёмник")
        if (vm.modemId != null) parts.add("Модем")
        return parts.joinToString(", ")
    }

    private fun buildInfoString(vm: VendingMachine, connType: String): String {
        return "${vm.model} | ${vm.typeDisplay} | ${connType.uppercase()} | ${vm.locationAddress ?: "—"}"
    }

    data class MonitorRow(
        val index: Int,
        val name: String,
        val status: String,
        val connection: String,
        val loadInfo: String,
        val money: String,
        val events: String,
        val equipment: String,
        val info: String,
        val extra: String,
        val machine: VendingMachine
    )
}
