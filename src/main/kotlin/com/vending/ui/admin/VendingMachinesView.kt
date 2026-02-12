package com.vending.ui.admin

import com.vending.dao.CompanyDAO
import com.vending.dao.ModemDAO
import com.vending.dao.VendingMachineDAO
import com.vending.model.VendingMachine
import com.vending.util.ExportUtil
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.stage.Stage

class VendingMachinesView(private val stage: Stage) {
    val root: BorderPane = BorderPane()
    private val tableView = TableView<VendingMachine>()
    private val tilePane = FlowPane()
    private val machines = FXCollections.observableArrayList<VendingMachine>()
    private var allMachines = listOf<VendingMachine>()
    private var isTableMode = true
    private var currentPage = 0
    private var pageSize = 25
    private var nameFilter = ""
    private val pageSizeChoices = listOf(10, 25, 50, 100)

    // Pagination labels
    private val recordsLabel = Label("0 из 0")
    private val pageLabel = Label("Стр. 1")

    init {
        root.styleClass.add("admin-view")
        buildToolbar()
        buildTable()
        buildTilePane()
        buildPagination()
        loadData()
        showTableMode()
    }

    private fun buildToolbar() {
        val toolbar = HBox(10.0).apply {
            styleClass.add("admin-toolbar")
            padding = Insets(12.0, 16.0, 12.0, 16.0)
            alignment = Pos.CENTER_LEFT
        }

        val title = Label("Торговые автоматы").apply { styleClass.add("page-title"); font = Font.font(18.0) }

        // Filter
        val filterField = TextField().apply {
            promptText = "🔍 Фильтр по названию"
            styleClass.add("filter-field")
            prefWidth = 220.0
        }
        filterField.textProperty().addListener { _, _, newVal ->
            nameFilter = newVal
            currentPage = 0
            applyFilter()
        }

        // Page size
        val pageSizeBox = ComboBox(FXCollections.observableArrayList(pageSizeChoices)).apply {
            value = pageSize
            setOnAction {
                pageSize = value
                currentPage = 0
                applyFilter()
            }
        }
        val pageSizeLabel = Label("Строк:")

        // View toggle
        val tableBtn = ToggleButton("📋 Таблица").apply { isSelected = true; styleClass.add("toggle-view-btn") }
        val tileBtn = ToggleButton("🔲 Плитка").apply { styleClass.add("toggle-view-btn") }
        val toggleGroup = ToggleGroup()
        tableBtn.toggleGroup = toggleGroup
        tileBtn.toggleGroup = toggleGroup
        toggleGroup.selectedToggleProperty().addListener { _, _, newVal ->
            if (newVal == tableBtn) showTableMode() else showTileMode()
        }

        // Export buttons
        val exportCSV = Button("CSV").apply {
            styleClass.add("export-btn")
            setOnAction { ExportUtil.exportToCSV(allMachines, stage) }
        }
        val exportPDF = Button("PDF").apply {
            styleClass.add("export-btn")
            setOnAction { ExportUtil.exportToPDF(allMachines, stage) }
        }
        val exportHTML = Button("HTML").apply {
            styleClass.add("export-btn")
            setOnAction { ExportUtil.exportToHTML(allMachines, stage) }
        }

        // Add button
        val addBtn = Button("+ Добавить ТА").apply {
            styleClass.add("primary-button")
            setOnAction { showAddDialog() }
        }

        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

        toolbar.children.addAll(
            title, filterField, pageSizeLabel, pageSizeBox,
            tableBtn, tileBtn, spacer,
            exportCSV, exportPDF, exportHTML, addBtn
        )
        root.top = toolbar
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildTable() {
        tableView.apply {
            styleClass.add("admin-table")
            items = machines
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY

            // Set row factory for alternating colors
            setRowFactory {
                object : TableRow<VendingMachine>() {
                    override fun updateItem(item: VendingMachine?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (item == null || empty) {
                            style = ""
                        } else {
                            style = if (index % 2 != 0) "-fx-background-color: #f5f8fa;" else ""
                        }
                    }
                }
            }
        }

        val idCol = TableColumn<VendingMachine, Int>("ID").apply {
            setCellValueFactory { javafx.beans.property.SimpleObjectProperty(it.value.id) }
            prefWidth = 50.0
        }
        val nameCol = TableColumn<VendingMachine, String>("Название").apply {
            setCellValueFactory { SimpleStringProperty(it.value.name) }
            prefWidth = 160.0
        }
        val modelCol = TableColumn<VendingMachine, String>("Модель").apply {
            setCellValueFactory { SimpleStringProperty(it.value.model) }
            prefWidth = 100.0
        }
        val companyCol = TableColumn<VendingMachine, String>("Компания").apply {
            setCellValueFactory { SimpleStringProperty(it.value.companyName) }
            prefWidth = 140.0
        }
        val modemCol = TableColumn<VendingMachine, String>("Модем").apply {
            setCellValueFactory { SimpleStringProperty(it.value.modemImei.ifBlank { "—" }) }
            prefWidth = 120.0
        }
        val addressCol = TableColumn<VendingMachine, String>("Адрес/Место").apply {
            setCellValueFactory { SimpleStringProperty(it.value.locationAddress ?: "") }
            prefWidth = 180.0
        }
        val dateCol = TableColumn<VendingMachine, String>("В работе с").apply {
            setCellValueFactory { SimpleStringProperty(it.value.commissioningDate.toString()) }
            prefWidth = 100.0
        }
        val statusCol = TableColumn<VendingMachine, String>("Статус").apply {
            setCellValueFactory { SimpleStringProperty(it.value.statusDisplay) }
            prefWidth = 110.0
            setCellFactory {
                object : TableCell<VendingMachine, String>() {
                    override fun updateItem(item: String?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (item == null || empty) {
                            text = null; graphic = null
                        } else {
                            text = item
                            val color = when (item) {
                                "Работает" -> "#50cd89"
                                "Не работает" -> "#f1416c"
                                "На обслуживании" -> "#ffc700"
                                else -> "#7239ea"
                            }
                            style = "-fx-text-fill: $color; -fx-font-weight: bold;"
                        }
                    }
                }
            }
        }

        // Actions column
        val actionsCol = TableColumn<VendingMachine, Void>("Действия").apply {
            prefWidth = 180.0
            setCellFactory {
                object : TableCell<VendingMachine, Void>() {
                    private val editBtn = Button("✏").apply { styleClass.add("action-btn") }
                    private val deleteBtn = Button("🗑").apply { styleClass.add("action-btn-danger") }
                    private val unbindBtn = Button("📡✕").apply { styleClass.add("action-btn-warn") }
                    private val box = HBox(4.0, editBtn, deleteBtn, unbindBtn)

                    init {
                        editBtn.setOnAction {
                            val vm = tableView.items[index]
                            showEditDialog(vm)
                        }
                        deleteBtn.setOnAction {
                            val vm = tableView.items[index]
                            confirmDelete(vm)
                        }
                        unbindBtn.setOnAction {
                            val vm = tableView.items[index]
                            confirmUnbindModem(vm)
                        }
                    }

                    override fun updateItem(item: Void?, empty: Boolean) {
                        super.updateItem(item, empty)
                        graphic = if (empty) null else box
                    }
                }
            }
        }

        tableView.columns.addAll(idCol, nameCol, modelCol, companyCol, modemCol, addressCol, dateCol, statusCol, actionsCol)
    }

    private fun buildTilePane() {
        tilePane.apply {
            hgap = 12.0
            vgap = 12.0
            padding = Insets(12.0)
        }
    }

    private fun buildPagination() {
        val paginationBar = HBox(10.0).apply {
            styleClass.add("pagination-bar")
            padding = Insets(8.0, 16.0, 8.0, 16.0)
            alignment = Pos.CENTER
        }

        val prevBtn = Button("◀ Назад").apply {
            styleClass.add("page-btn")
            setOnAction {
                if (currentPage > 0) { currentPage--; applyFilter() }
            }
        }
        val nextBtn = Button("Вперёд ▶").apply {
            styleClass.add("page-btn")
            setOnAction {
                val maxPage = ((allMachines.size - 1) / pageSize).coerceAtLeast(0)
                if (currentPage < maxPage) { currentPage++; applyFilter() }
            }
        }

        paginationBar.children.addAll(recordsLabel, Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            prevBtn, pageLabel, nextBtn)
        root.bottom = paginationBar
    }

    private fun showTableMode() {
        isTableMode = true
        root.center = tableView
    }

    private fun showTileMode() {
        isTableMode = false
        updateTiles()
        root.center = ScrollPane(tilePane).apply { isFitToWidth = true }
    }

    private fun updateTiles() {
        tilePane.children.clear()
        machines.forEach { vm ->
            val tile = VBox(6.0).apply {
                styleClass.add("machine-tile")
                padding = Insets(12.0)
                prefWidth = 250.0
                val statusColor = when (vm.status) {
                    "working" -> "#50cd89"
                    "broken" -> "#f1416c"
                    "maintenance" -> "#ffc700"
                    else -> "#7239ea"
                }
                children.addAll(
                    HBox(8.0).apply {
                        children.addAll(
                            Label("●").apply { style = "-fx-text-fill: $statusColor; -fx-font-size: 16;" },
                            Label(vm.name).apply { styleClass.add("tile-machine-name") }
                        )
                    },
                    Label("Модель: ${vm.model}").apply { styleClass.add("tile-info") },
                    Label("Компания: ${vm.companyName}").apply { styleClass.add("tile-info") },
                    Label("Адрес: ${vm.locationAddress ?: "—"}").apply { styleClass.add("tile-info"); isWrapText = true },
                    Label("Доход: ${vm.totalRevenue} ₽").apply { styleClass.add("tile-info") },
                    HBox(4.0).apply {
                        val editBtn = Button("✏").apply {
                            styleClass.add("action-btn")
                            setOnAction { showEditDialog(vm) }
                        }
                        val deleteBtn = Button("🗑").apply {
                            styleClass.add("action-btn-danger")
                            setOnAction { confirmDelete(vm) }
                        }
                        children.addAll(editBtn, deleteBtn)
                    }
                )
            }
            tilePane.children.add(tile)
        }
    }

    private fun loadData() {
        Thread {
            try {
                allMachines = VendingMachineDAO.findAll()
                Platform.runLater { applyFilter() }
            } catch (e: Exception) {
                Platform.runLater {
                    machines.clear()
                    recordsLabel.text = "Ошибка загрузки данных"
                }
            }
        }.start()
    }

    private fun applyFilter() {
        val filtered = if (nameFilter.isBlank()) allMachines
        else allMachines.filter { it.name.contains(nameFilter, ignoreCase = true) }

        val totalRecords = filtered.size
        val totalPages = ((totalRecords - 1) / pageSize).coerceAtLeast(0)
        if (currentPage > totalPages) currentPage = totalPages

        val fromIndex = currentPage * pageSize
        val toIndex = minOf(fromIndex + pageSize, totalRecords)
        val pageData = if (fromIndex < totalRecords) filtered.subList(fromIndex, toIndex) else emptyList()

        machines.setAll(pageData)
        recordsLabel.text = "Показано ${if (pageData.isEmpty()) 0 else fromIndex + 1}–$toIndex из $totalRecords"
        pageLabel.text = "Стр. ${currentPage + 1} из ${totalPages + 1}"

        if (!isTableMode) updateTiles()
    }

    private fun showAddDialog() {
        AddMachineDialog(stage, null) { loadData() }.show()
    }

    private fun showEditDialog(vm: VendingMachine) {
        AddMachineDialog(stage, vm) { loadData() }.show()
    }

    private fun confirmDelete(vm: VendingMachine) {
        val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Удаление ТА"
            headerText = "Удалить торговый автомат?"
            contentText = "Вы уверены, что хотите удалить «${vm.name}» (${vm.serialNumber})?"
        }
        alert.showAndWait().ifPresent { result ->
            if (result == ButtonType.OK) {
                Thread {
                    try {
                        VendingMachineDAO.delete(vm.id)
                        Platform.runLater { loadData() }
                    } catch (e: Exception) {
                        Platform.runLater {
                            Alert(Alert.AlertType.ERROR, "Ошибка удаления: ${e.message}").showAndWait()
                        }
                    }
                }.start()
            }
        }
    }

    private fun confirmUnbindModem(vm: VendingMachine) {
        if (vm.modemId == null) {
            Alert(Alert.AlertType.INFORMATION, "У данного ТА нет привязанного модема.").showAndWait()
            return
        }
        val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Отвязка модема"
            headerText = "Отвязать модем от ТА?"
            contentText = "Вы уверены, что хотите отвязать модем (${vm.modemImei}) от «${vm.name}»?"
        }
        alert.showAndWait().ifPresent { result ->
            if (result == ButtonType.OK) {
                Thread {
                    try {
                        VendingMachineDAO.unbindModem(vm.id)
                        Platform.runLater {
                            Alert(Alert.AlertType.INFORMATION, "Модем успешно отвязан от ТА «${vm.name}».").showAndWait()
                            loadData()
                        }
                    } catch (e: Exception) {
                        Platform.runLater {
                            Alert(Alert.AlertType.ERROR, "Ошибка отвязки: ${e.message}").showAndWait()
                        }
                    }
                }.start()
            }
        }
    }
}
