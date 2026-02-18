package com.vending.ui

import com.vending.dao.MachineProductDAO
import com.vending.dao.ProductDAO
import com.vending.dao.VendingMachineDAO
import com.vending.model.MachineProduct
import com.vending.model.Product
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

class InventoryView {
    val root: BorderPane = BorderPane()
    private val df = DecimalFormat("#,##0.00")
    private val dtFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    // Products tab
    private val productsTable = TableView<Product>()
    private val productsData = FXCollections.observableArrayList<Product>()

    // Stock tab
    private val stockTable = TableView<MachineProduct>()
    private val stockData = FXCollections.observableArrayList<MachineProduct>()

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
            Tab("Каталог продуктов", buildProductsTab()),
            Tab("Остатки в автоматах", buildStockTab()),
            Tab("Низкий остаток", buildLowStockTab())
        )

        val toolbar = HBox(10.0).apply {
            styleClass.add("admin-toolbar")
            padding = Insets(12.0, 20.0, 12.0, 20.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Учёт ТМЦ").apply { font = Font.font(18.0); styleClass.add("page-title") }
            )
        }

        root.top = toolbar
        root.center = tabPane
    }

    // ================== Products Catalog Tab ==================

    private fun buildProductsTab(): BorderPane {
        val pane = BorderPane()

        val addBtn = Button("+ Добавить продукт").apply {
            styleClass.add("primary-button")
            setOnAction { showProductDialog(null) }
        }

        val searchField = TextField().apply {
            promptText = "Поиск по названию / штрихкоду…"
            styleClass.add("filter-field")
            prefWidth = 260.0
        }

        val categoryCombo = ComboBox<String>().apply {
            items.addAll("Все", "Напитки", "Снеки", "Горячие напитки", "Другое")
            value = "Все"
            prefWidth = 150.0
        }

        val filterBtn = Button("Поиск").apply {
            styleClass.add("primary-button")
            setOnAction {
                val query = searchField.text.trim().lowercase()
                val cat = if (categoryCombo.value == "Все") null else categoryCombo.value
                val filtered = productsData.toList().let { _ ->
                    ProductDAO.findAll().filter { p ->
                        val matchQ = query.isEmpty() ||
                                p.name.lowercase().contains(query) ||
                                (p.barcode?.lowercase()?.contains(query) == true)
                        val matchC = cat == null || p.category == cat
                        matchQ && matchC
                    }
                }
                productsData.setAll(filtered)
            }
        }

        val filterBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                searchField, categoryCombo, filterBtn,
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                addBtn
            )
        }

        productsTable.items = productsData
        productsTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        productsTable.styleClass.add("admin-table")
        productsTable.placeholder = Label("Нет продуктов в каталоге")

        productsTable.columns.addAll(
            TableColumn<Product, String>("ID").apply {
                setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }; prefWidth = 50.0
            },
            TableColumn<Product, String>("Название").apply {
                setCellValueFactory { SimpleStringProperty(it.value.name) }; prefWidth = 200.0
            },
            TableColumn<Product, String>("Цена").apply {
                setCellValueFactory { SimpleStringProperty("${df.format(it.value.price)} ₽") }; prefWidth = 100.0
            },
            TableColumn<Product, String>("Категория").apply {
                setCellValueFactory { SimpleStringProperty(it.value.category ?: "—") }; prefWidth = 130.0
            },
            TableColumn<Product, String>("Штрихкод").apply {
                setCellValueFactory { SimpleStringProperty(it.value.barcode ?: "—") }; prefWidth = 130.0
            },
            TableColumn<Product, String>("Мин. остаток").apply {
                setCellValueFactory { SimpleStringProperty(it.value.minStock.toString()) }; prefWidth = 90.0
            },
            TableColumn<Product, Void>("Действия").apply {
                prefWidth = 120.0
                setCellFactory {
                    object : TableCell<Product, Void>() {
                        private val editBtn = Button("✏").apply { styleClass.add("action-btn") }
                        private val delBtn = Button("🗑").apply { styleClass.add("action-btn-danger") }
                        private val box = HBox(4.0, editBtn, delBtn)
                        init {
                            editBtn.setOnAction {
                                val idx = index
                                if (idx >= 0 && idx < productsTable.items.size) {
                                    showProductDialog(productsTable.items[idx])
                                }
                            }
                            delBtn.setOnAction {
                                val idx = index
                                if (idx >= 0 && idx < productsTable.items.size) {
                                    confirmDeleteProduct(productsTable.items[idx])
                                }
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
        pane.center = productsTable
        return pane
    }

    // ================== Stock Tab ==================

    private fun buildStockTab(): BorderPane {
        val pane = BorderPane()

        val machineCombo = ComboBox<String>().apply {
            items.add("Все автоматы")
            prefWidth = 250.0
            value = "Все автоматы"
        }

        // Load machines list
        Thread {
            try {
                val machines = VendingMachineDAO.findAll()
                Platform.runLater {
                    machines.forEach { machineCombo.items.add("${it.id}: ${it.name}") }
                }
            } catch (_: Exception) {}
        }.start()

        val filterBtn = Button("Показать").apply {
            styleClass.add("primary-button")
            setOnAction {
                val selected = machineCombo.value
                Thread {
                    try {
                        val data = if (selected == "Все автоматы") {
                            MachineProductDAO.findAll()
                        } else {
                            val mId = selected.substringBefore(":").trim().toIntOrNull() ?: return@Thread
                            MachineProductDAO.findByMachine(mId)
                        }
                        Platform.runLater { stockData.setAll(data) }
                    } catch (_: Exception) {}
                }.start()
            }
        }

        val filterBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Автомат:").apply { styleClass.add("filter-group-label") },
                machineCombo, filterBtn
            )
        }

        stockTable.items = stockData
        stockTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        stockTable.styleClass.add("admin-table")
        stockTable.placeholder = Label("Выберите автомат для просмотра остатков")

        stockTable.columns.addAll(
            TableColumn<MachineProduct, String>("Автомат").apply {
                setCellValueFactory { SimpleStringProperty(it.value.machineName) }; prefWidth = 180.0
            },
            TableColumn<MachineProduct, String>("Продукт").apply {
                setCellValueFactory { SimpleStringProperty(it.value.productName) }; prefWidth = 180.0
            },
            TableColumn<MachineProduct, String>("Кол-во").apply {
                setCellValueFactory { SimpleStringProperty(it.value.quantity.toString()) }; prefWidth = 80.0
                setCellFactory {
                    object : TableCell<MachineProduct, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; style = "" }
                            else {
                                val mp = tableView.items.getOrNull(index)
                                text = item
                                style = if (mp != null && mp.quantity <= mp.minStock)
                                    "-fx-text-fill: #ef6b6b; -fx-font-weight: bold;"
                                else "-fx-text-fill: #50cd89;"
                            }
                        }
                    }
                }
            },
            TableColumn<MachineProduct, String>("Макс. ёмкость").apply {
                setCellValueFactory { SimpleStringProperty(it.value.maxCapacity.toString()) }; prefWidth = 100.0
            },
            TableColumn<MachineProduct, String>("Заполнение").apply {
                prefWidth = 130.0
                setCellValueFactory { SimpleStringProperty("") }
                setCellFactory {
                    object : TableCell<MachineProduct, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (empty) { graphic = null; text = null }
                            else {
                                val mp = tableView.items.getOrNull(index)
                                if (mp != null && mp.maxCapacity > 0) {
                                    val pct = mp.quantity.toDouble() / mp.maxCapacity.toDouble()
                                    val bar = ProgressBar(pct).apply {
                                        prefWidth = 80.0; prefHeight = 14.0
                                        val barColor = when {
                                            pct >= 0.7 -> "#50cd89"
                                            pct >= 0.3 -> "#ffc700"
                                            else -> "#f1416c"
                                        }
                                        style = "-fx-accent: $barColor;"
                                    }
                                    val label = Label("${(pct * 100).toInt()}%").apply { font = Font.font(10.0) }
                                    graphic = HBox(4.0, bar, label).apply { alignment = Pos.CENTER_LEFT }
                                    text = null
                                } else {
                                    text = "—"
                                    graphic = null
                                }
                            }
                        }
                    }
                }
            },
            TableColumn<MachineProduct, String>("Мин. остаток").apply {
                setCellValueFactory { SimpleStringProperty(it.value.minStock.toString()) }; prefWidth = 90.0
            },
            TableColumn<MachineProduct, String>("Пополнение").apply {
                setCellValueFactory {
                    SimpleStringProperty(it.value.lastRestock?.format(dtFmt) ?: "—")
                }; prefWidth = 140.0
            }
        )

        pane.top = filterBar
        pane.center = stockTable
        return pane
    }

    // ================== Low Stock Tab ==================

    private val lowStockTable = TableView<MachineProduct>()
    private val lowStockData = FXCollections.observableArrayList<MachineProduct>()

    private fun buildLowStockTab(): BorderPane {
        val pane = BorderPane()

        val refreshBtn = Button("Обновить").apply {
            styleClass.add("primary-button")
            setOnAction { loadLowStock() }
        }

        val infoBar = HBox(10.0).apply {
            padding = Insets(10.0, 16.0, 10.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("⚠ Позиции с остатком ниже минимального").apply {
                    styleClass.add("filter-group-label")
                    style = "-fx-text-fill: #f0b75a;"
                },
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                refreshBtn
            )
        }

        lowStockTable.items = lowStockData
        lowStockTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        lowStockTable.styleClass.add("admin-table")
        lowStockTable.placeholder = Label("Все запасы в норме")

        lowStockTable.columns.addAll(
            TableColumn<MachineProduct, String>("Автомат").apply {
                setCellValueFactory { SimpleStringProperty(it.value.machineName) }; prefWidth = 180.0
            },
            TableColumn<MachineProduct, String>("Продукт").apply {
                setCellValueFactory { SimpleStringProperty(it.value.productName) }; prefWidth = 180.0
            },
            TableColumn<MachineProduct, String>("Остаток").apply {
                setCellValueFactory { SimpleStringProperty(it.value.quantity.toString()) }; prefWidth = 90.0
                setCellFactory {
                    object : TableCell<MachineProduct, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            text = item
                            style = if (item != null && !empty) "-fx-text-fill: #ef6b6b; -fx-font-weight: bold;" else ""
                        }
                    }
                }
            },
            TableColumn<MachineProduct, String>("Минимум").apply {
                setCellValueFactory { SimpleStringProperty(it.value.minStock.toString()) }; prefWidth = 90.0
            },
            TableColumn<MachineProduct, String>("Ёмкость").apply {
                setCellValueFactory { SimpleStringProperty(it.value.maxCapacity.toString()) }; prefWidth = 90.0
            },
            TableColumn<MachineProduct, String>("Нужно дозагрузить").apply {
                setCellValueFactory {
                    val need = it.value.maxCapacity - it.value.quantity
                    SimpleStringProperty(if (need > 0) "+$need" else "0")
                }; prefWidth = 120.0
                setCellFactory {
                    object : TableCell<MachineProduct, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            text = item
                            style = if (item != null && !empty) "-fx-text-fill: #f0b75a; -fx-font-weight: bold;" else ""
                        }
                    }
                }
            }
        )

        pane.top = infoBar
        pane.center = lowStockTable
        return pane
    }

    // ================== Dialogs ==================

    private fun showProductDialog(p: Product?) {
        val dialog = Dialog<Product>().apply {
            title = if (p == null) "Новый продукт" else "Редактирование"
            headerText = if (p == null) "Добавление продукта" else "Редактирование «${p.name}»"
        }

        val nameF = TextField(p?.name ?: "").apply { promptText = "Название" }
        val priceF = TextField(p?.price?.toPlainString() ?: "").apply { promptText = "Цена" }
        val categoryF = ComboBox<String>().apply {
            items.addAll("Напитки", "Снеки", "Горячие напитки", "Другое")
            value = p?.category ?: "Напитки"
            isEditable = true
        }
        val barcodeF = TextField(p?.barcode ?: "").apply { promptText = "Штрихкод" }
        val minStockF = TextField((p?.minStock ?: 5).toString()).apply { promptText = "Мин. остаток" }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; padding = Insets(16.0)
            add(Label("Название *"), 0, 0); add(nameF, 1, 0)
            add(Label("Цена *"), 0, 1); add(priceF, 1, 1)
            add(Label("Категория"), 0, 2); add(categoryF, 1, 2)
            add(Label("Штрихкод"), 0, 3); add(barcodeF, 1, 3)
            add(Label("Мин. остаток"), 0, 4); add(minStockF, 1, 4)
        }

        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.setResultConverter { btn ->
            if (btn == ButtonType.OK && nameF.text.isNotBlank() && priceF.text.isNotBlank()) {
                try {
                    Product(
                        id = p?.id ?: 0,
                        name = nameF.text.trim(),
                        price = BigDecimal(priceF.text.trim()),
                        category = categoryF.value?.trim()?.ifBlank { null },
                        barcode = barcodeF.text.trim().ifBlank { null },
                        minStock = minStockF.text.trim().toIntOrNull() ?: 5
                    )
                } catch (_: Exception) { null }
            } else null
        }

        dialog.showAndWait().ifPresent { product ->
            Thread {
                try {
                    if (p != null) {
                        ProductDAO.update(product.id, product.name, product.price, product.category, product.barcode, product.minStock)
                    } else {
                        ProductDAO.create(product.name, product.price, product.category, product.barcode, product.minStock)
                    }
                    Platform.runLater { loadProducts() }
                } catch (e: Exception) {
                    Platform.runLater { Alert(Alert.AlertType.ERROR, "Ошибка: ${e.message}").showAndWait() }
                }
            }.start()
        }
    }

    private fun confirmDeleteProduct(p: Product) {
        Alert(Alert.AlertType.CONFIRMATION, "Удалить продукт «${p.name}»?").showAndWait().ifPresent {
            if (it == ButtonType.OK) {
                Thread {
                    try { ProductDAO.delete(p.id); Platform.runLater { loadProducts() } }
                    catch (e: Exception) { Platform.runLater { Alert(Alert.AlertType.ERROR, e.message ?: "Ошибка").showAndWait() } }
                }.start()
            }
        }
    }

    // ================== Data Loading ==================

    private fun loadData() {
        loadProducts()
        loadStock()
        loadLowStock()
    }

    private fun loadProducts() {
        Thread {
            try {
                val list = ProductDAO.findAll()
                Platform.runLater { productsData.setAll(list) }
            } catch (_: Exception) {}
        }.start()
    }

    private fun loadStock() {
        Thread {
            try {
                val list = MachineProductDAO.findAll()
                Platform.runLater { stockData.setAll(list) }
            } catch (_: Exception) {}
        }.start()
    }

    private fun loadLowStock() {
        Thread {
            try {
                val list = MachineProductDAO.findLowStock()
                Platform.runLater { lowStockData.setAll(list) }
            } catch (_: Exception) {}
        }.start()
    }
}
