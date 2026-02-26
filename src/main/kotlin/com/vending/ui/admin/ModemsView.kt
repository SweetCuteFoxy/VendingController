package com.vending.ui.admin

import com.vending.dao.ModemDAO
import com.vending.model.Modem
import com.vending.util.ExportUtil
import javafx.stage.Stage
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import org.slf4j.LoggerFactory

class ModemsView {
    val root: BorderPane = BorderPane()
    private val logger = LoggerFactory.getLogger(ModemsView::class.java)
    private val table = TableView<Modem>()
    private val data = FXCollections.observableArrayList<Modem>()

    init {
        root.styleClass.add("admin-view")
        buildToolbar()
        buildTable()
        loadData()
    }

    private fun buildToolbar() {
        val toolbar = HBox(10.0).apply {
            styleClass.add("admin-toolbar")
            padding = Insets(12.0, 20.0, 12.0, 20.0)
            alignment = Pos.CENTER_LEFT
        }
        val title = Label("Модемы").apply { font = Font.font(18.0); styleClass.add("page-title") }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val exportBtn = Button("📥 CSV").apply {
            styleClass.add("export-btn")
            setOnAction {
                val stage = table.scene?.window as? Stage ?: return@setOnAction
                ExportUtil.exportGenericCSV(
                    data = data.toList(),
                    headers = listOf("ID", "IMEI", "Телефон", "Статус"),
                    rowExtractor = { listOf(it.id.toString(), it.imei, it.phoneNumber ?: "", it.status) },
                    fileName = "modems.csv",
                    stage = stage
                )
            }
        }
        val addBtn = Button("+ Добавить").apply {
            styleClass.add("primary-button")
            setOnAction { showDialog(null) }
        }
        toolbar.children.addAll(title, spacer, exportBtn, addBtn)
        root.top = toolbar
    }

    private fun buildTable() {
        table.items = data
        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        table.styleClass.add("admin-table")

        // Row styling handled by CSS

        table.columns.addAll(
            TableColumn<Modem, String>("ID").apply {
                setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }; prefWidth = 60.0
            },
            TableColumn<Modem, String>("IMEI").apply {
                setCellValueFactory { SimpleStringProperty(it.value.imei) }; prefWidth = 200.0
            },
            TableColumn<Modem, String>("Номер телефона").apply {
                setCellValueFactory { SimpleStringProperty(it.value.phoneNumber ?: "") }; prefWidth = 160.0
            },
            TableColumn<Modem, String>("Статус").apply {
                setCellValueFactory { SimpleStringProperty(if (it.value.status == "active") "Активен" else "Неактивен") }
                prefWidth = 120.0
                setCellFactory {
                    object : TableCell<Modem, String>() {
                        override fun updateItem(item: String?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if (item == null || empty) { text = null; style = "" }
                            else {
                                text = item
                                style = if (item == "Активен") "-fx-text-fill: #50cd89; -fx-font-weight: bold;"
                                else "-fx-text-fill: #f1416c; -fx-font-weight: bold;"
                            }
                        }
                    }
                }
            },
            TableColumn<Modem, Void>("Действия").apply {
                prefWidth = 120.0
                setCellFactory {
                    object : TableCell<Modem, Void>() {
                        private val editBtn = Button("✏").apply { styleClass.add("action-btn") }
                        private val delBtn = Button("🗑").apply { styleClass.add("action-btn-danger") }
                        private val box = HBox(4.0, editBtn, delBtn)
                        init {
                            editBtn.setOnAction {
                                val idx = index
                                if (idx in 0 until table.items.size) showDialog(table.items[idx])
                            }
                            delBtn.setOnAction {
                                val idx = index
                                if (idx in 0 until table.items.size) confirmDelete(table.items[idx])
                            }
                        }
                        override fun updateItem(item: Void?, empty: Boolean) {
                            super.updateItem(item, empty); graphic = if (empty) null else box
                        }
                    }
                }
            }
        )
        root.center = table
    }

    private fun loadData() {
        table.placeholder = Label("Загрузка…")
        Thread {
            try {
                val list = ModemDAO.findAll()
                Platform.runLater {
                    data.setAll(list)
                    table.placeholder = Label("Нет модемов")
                }
            } catch (e: Exception) {
                logger.error("Failed to load modems", e)
                Platform.runLater { table.placeholder = Label("Ошибка загрузки: ${e.message}") }
            }
        }.start()
    }

    private fun showDialog(m: Modem?) {
        val dialog = Dialog<Modem>().apply {
            title = if (m == null) "Новый модем" else "Редактирование модема"
            headerText = null
        }
        val imeiF = TextField(m?.imei ?: "").apply { styleClass.add("edit-field"); promptText = "IMEI (15 цифр)" }
        val phoneF = TextField(m?.phoneNumber ?: "").apply { styleClass.add("edit-field"); promptText = "+7XXXXXXXXXX" }
        val statusBox = ComboBox(FXCollections.observableArrayList("Активен", "Неактивен")).apply {
            styleClass.add("edit-combo"); maxWidth = Double.MAX_VALUE
            value = if (m?.status == "active") "Активен" else "Неактивен"
        }
        val grid = GridPane().apply {
            styleClass.add("form-grid")
            hgap = 12.0; vgap = 10.0; padding = Insets(20.0, 24.0, 8.0, 24.0)
            columnConstraints.addAll(ColumnConstraints(100.0), ColumnConstraints(260.0))
            add(Label("IMEI *").apply { styleClass.add("form-label") }, 0, 0); add(imeiF, 1, 0)
            add(Label("Телефон").apply { styleClass.add("form-label") }, 0, 1); add(phoneF, 1, 1)
            add(Label("Статус").apply { styleClass.add("form-label") }, 0, 2); add(statusBox, 1, 2)
        }
        dialog.dialogPane.apply {
            styleClass.add("styled-dialog")
            content = VBox(8.0).apply {
                children.addAll(Label(if (m == null) "Новый модем" else "Редактирование: ${m.imei}").apply { styleClass.add("dialog-form-title") }, Separator(), grid)
                padding = Insets(4.0, 0.0, 0.0, 0.0)
            }
            buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            (lookupButton(ButtonType.OK) as Button).apply { text = if (m == null) "Создать" else "Сохранить"; styleClass.add("btn-save") }
            (lookupButton(ButtonType.CANCEL) as Button).apply { text = "Отмена"; styleClass.add("btn-cancel") }
            stylesheets.addAll(table.scene?.stylesheets ?: emptyList<String>())
        }

        dialog.setResultConverter { btn ->
            if (btn == ButtonType.OK) {
                if (imeiF.text.isBlank() || !imeiF.text.matches(Regex("^\\d{15}$"))) {
                    Alert(Alert.AlertType.WARNING, "IMEI должен содержать 15 цифр").showAndWait()
                    return@setResultConverter null
                }
                val statusVal = if (statusBox.value == "Активен") "active" else "inactive"
                Modem(m?.id ?: 0, imeiF.text.trim(), phoneF.text.trim().ifBlank { null }, statusVal)
            } else null
        }

        dialog.showAndWait().ifPresent { modem ->
            Thread {
                try {
                    if (m != null) ModemDAO.update(modem.id, modem.imei, modem.phoneNumber, modem.status)
                    else ModemDAO.create(modem.imei, modem.phoneNumber, modem.status)
                    Platform.runLater { loadData() }
                } catch (e: Exception) {
                    Platform.runLater { Alert(Alert.AlertType.ERROR, "Ошибка: ${e.message}").showAndWait() }
                }
            }.start()
        }
    }

    private fun confirmDelete(m: Modem) {
        Alert(Alert.AlertType.CONFIRMATION, "Удалить модем ${m.imei}?").showAndWait().ifPresent {
            if (it == ButtonType.OK) {
                Thread {
                    try { ModemDAO.delete(m.id); Platform.runLater { loadData() } }
                    catch (e: Exception) { Platform.runLater { Alert(Alert.AlertType.ERROR, e.message ?: "Ошибка").showAndWait() } }
                }.start()
            }
        }
    }
}
