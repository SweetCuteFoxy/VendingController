package com.vending.ui.admin

import com.vending.dao.CompanyDAO
import com.vending.model.Company
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import org.slf4j.LoggerFactory

class CompaniesView {
    val root: BorderPane = BorderPane()
    private val logger = LoggerFactory.getLogger(CompaniesView::class.java)
    private val table = TableView<Company>()
    private val data = FXCollections.observableArrayList<Company>()

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
        val title = Label("Компании").apply { font = Font.font(18.0); styleClass.add("page-title") }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val addBtn = Button("+ Добавить").apply {
            styleClass.add("primary-button")
            setOnAction { showAddDialog() }
        }
        toolbar.children.addAll(title, spacer, addBtn)
        root.top = toolbar
    }

    private fun buildTable() {
        table.items = data
        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        table.styleClass.add("admin-table")

        table.columns.addAll(
            TableColumn<Company, String>("ID").apply {
                setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }; prefWidth = 50.0
            },
            TableColumn<Company, String>("Название").apply {
                setCellValueFactory { SimpleStringProperty(it.value.name) }; prefWidth = 200.0
            },
            TableColumn<Company, String>("Адрес").apply {
                setCellValueFactory { SimpleStringProperty(it.value.address ?: "") }; prefWidth = 200.0
            },
            TableColumn<Company, String>("Телефон").apply {
                setCellValueFactory { SimpleStringProperty(it.value.contactPhone ?: "") }; prefWidth = 130.0
            },
            TableColumn<Company, String>("Email").apply {
                setCellValueFactory { SimpleStringProperty(it.value.email ?: "") }; prefWidth = 160.0
            },
            TableColumn<Company, Void>("Действия").apply {
                prefWidth = 120.0
                setCellFactory {
                    object : TableCell<Company, Void>() {
                        private val editBtn = Button("✏").apply { styleClass.add("action-btn") }
                        private val delBtn = Button("🗑").apply { styleClass.add("action-btn-danger") }
                        private val box = HBox(4.0, editBtn, delBtn)
                        init {
                            editBtn.setOnAction {
                                val idx = index
                                if (idx in 0 until table.items.size) showEditDialog(table.items[idx])
                            }
                            delBtn.setOnAction {
                                val idx = index
                                if (idx in 0 until table.items.size) confirmDelete(table.items[idx])
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
        root.center = table
    }

    private fun loadData() {
        table.placeholder = Label("Загрузка…")
        Thread {
            try {
                val list = CompanyDAO.findAll()
                Platform.runLater {
                    data.setAll(list)
                    table.placeholder = Label("Нет компаний")
                }
            } catch (e: Exception) {
                logger.error("Failed to load companies", e)
                Platform.runLater { table.placeholder = Label("Ошибка загрузки: ${e.message}") }
            }
        }.start()
    }

    private fun showAddDialog() { showDialog(null) }
    private fun showEditDialog(c: Company) { showDialog(c) }

    private fun showDialog(c: Company?) {
        val dialog = Dialog<Company>().apply {
            title = if (c == null) "Новая компания" else "Редактирование"
            headerText = if (c == null) "Добавление компании" else "Редактирование «${c.name}»"
        }
        val nameF = TextField(c?.name ?: "").apply { promptText = "Название" }
        val addrF = TextField(c?.address ?: "").apply { promptText = "Адрес" }
        val phoneF = TextField(c?.contactPhone ?: "").apply { promptText = "Телефон" }
        val emailF = TextField(c?.email ?: "").apply { promptText = "Email" }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; padding = Insets(16.0)
            add(Label("Название *"), 0, 0); add(nameF, 1, 0)
            add(Label("Адрес"), 0, 1); add(addrF, 1, 1)
            add(Label("Телефон"), 0, 2); add(phoneF, 1, 2)
            add(Label("Email"), 0, 3); add(emailF, 1, 3)
        }
        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.setResultConverter { btn ->
            if (btn == ButtonType.OK && nameF.text.isNotBlank()) {
                Company(c?.id ?: 0, nameF.text.trim(), addrF.text.trim().ifBlank { null },
                    phoneF.text.trim().ifBlank { null }, emailF.text.trim().ifBlank { null })
            } else null
        }
        dialog.showAndWait().ifPresent { company ->
            Thread {
                try {
                    if (c != null) CompanyDAO.update(company.id, company.name, company.address, company.contactPhone, company.email)
                    else CompanyDAO.create(company.name, company.address, company.contactPhone, company.email)
                    Platform.runLater { loadData() }
                } catch (e: Exception) {
                    Platform.runLater { Alert(Alert.AlertType.ERROR, "Ошибка: ${e.message}").showAndWait() }
                }
            }.start()
        }
    }

    private fun confirmDelete(c: Company) {
        Alert(Alert.AlertType.CONFIRMATION, "Удалить компанию «${c.name}»?").showAndWait().ifPresent {
            if (it == ButtonType.OK) {
                Thread {
                    try { CompanyDAO.delete(c.id); Platform.runLater { loadData() } }
                    catch (e: Exception) { Platform.runLater { Alert(Alert.AlertType.ERROR, e.message ?: "Ошибка").showAndWait() } }
                }.start()
            }
        }
    }
}
