package com.vending.ui.admin

import com.vending.dao.CompanyDAO
import com.vending.dao.UserDAO
import com.vending.model.User
import com.vending.model.Role
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

class UsersView {
    val root: BorderPane = BorderPane()
    private val logger = LoggerFactory.getLogger(UsersView::class.java)
    private val table = TableView<User>()
    private val data = FXCollections.observableArrayList<User>()
    private var roles = listOf<Role>()

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
        val title = Label("Пользователи").apply { font = Font.font(18.0); styleClass.add("page-title") }
        val searchField = javafx.scene.control.TextField().apply {
            promptText = "🔍 ФИО / Email…"
            styleClass.add("filter-field")
            prefWidth = 220.0
            textProperty().addListener { _, _, q ->
                val lq = q.trim().lowercase()
                table.items = if (lq.isEmpty()) data
                else javafx.collections.FXCollections.observableList(data.filter {
                    it.fullName.lowercase().contains(lq) || it.email.lowercase().contains(lq)
                })
            }
        }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val exportBtn = Button("📥 CSV").apply {
            styleClass.add("export-btn")
            setOnAction {
                val items = data.toList()
                Thread {
                    ExportUtil.exportGenericCSV(
                        items,
                        listOf("ID", "ФИО", "Email", "Телефон", "Роль", "Компания", "Статус"),
                        { listOf(it.id.toString(), it.fullName, it.email, it.phone ?: "", it.roleName, it.companyName, if (it.isActive) "Активен" else "Неактивен") },
                        "users.csv"
                    )
                }.start()
            }
        }
        val addBtn = Button("+ Добавить").apply {
            styleClass.add("primary-button")
            setOnAction { showDialog(null) }
        }
        toolbar.children.addAll(title, searchField, spacer, exportBtn, addBtn)
        root.top = toolbar
    }

    private fun buildTable() {
        table.items = data
        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        table.styleClass.add("admin-table")

        // Row styling handled by CSS

        table.columns.addAll(
            TableColumn<User, String>("ID").apply {
                setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }; prefWidth = 50.0
            },
            TableColumn<User, String>("ФИО").apply {
                setCellValueFactory { SimpleStringProperty(it.value.fullName) }; prefWidth = 200.0
            },
            TableColumn<User, String>("Email").apply {
                setCellValueFactory { SimpleStringProperty(it.value.email) }; prefWidth = 180.0
            },
            TableColumn<User, String>("Телефон").apply {
                setCellValueFactory { SimpleStringProperty(it.value.phone ?: "") }; prefWidth = 130.0
            },
            TableColumn<User, String>("Роль").apply {
                setCellValueFactory { SimpleStringProperty(it.value.roleName) }; prefWidth = 120.0
            },
            TableColumn<User, String>("Компания").apply {
                setCellValueFactory { SimpleStringProperty(it.value.companyName) }; prefWidth = 140.0
            },
            TableColumn<User, String>("Статус").apply {
                setCellValueFactory { SimpleStringProperty(if (it.value.isActive) "Активен" else "Неактивен") }
                prefWidth = 90.0
                setCellFactory {
                    object : TableCell<User, String>() {
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
            TableColumn<User, Void>("Действия").apply {
                prefWidth = 120.0
                setCellFactory {
                    object : TableCell<User, Void>() {
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
                val list = UserDAO.findAll()
                roles = UserDAO.getAllRoles()
                Platform.runLater {
                    data.setAll(list)
                    table.placeholder = Label("Нет пользователей")
                }
            } catch (e: Exception) {
                logger.error("Failed to load users", e)
                Platform.runLater { table.placeholder = Label("Ошибка загрузки: ${e.message}") }
            }
        }.start()
    }

    private fun showDialog(u: User?) {
        val dialog = Dialog<Boolean>().apply {
            title = if (u == null) "Новый пользователь" else "Редактирование"
            headerText = if (u == null) "Добавление пользователя" else "Редактирование «${u.fullName}»"
        }
        val nameF = TextField(u?.fullName ?: "").apply { promptText = "ФИО" }
        val emailF = TextField(u?.email ?: "").apply { promptText = "Email" }
        val phoneF = TextField(u?.phone ?: "").apply { promptText = "Телефон" }
        val passF = PasswordField().apply { promptText = if (u == null) "Пароль" else "Новый пароль (оставьте пустым)" }
        val roleBox = ComboBox(FXCollections.observableArrayList(roles.map { "${it.id}: ${it.name}" }))
        if (u != null) roles.find { it.id == u.roleId }?.let { roleBox.value = "${it.id}: ${it.name}" }
        val companies = try { CompanyDAO.findAll() } catch (e: Exception) {
            logger.warn("Failed to load companies for dialog", e)
            emptyList()
        }
        val companyItems = mutableListOf("— Нет —")
        companyItems.addAll(companies.map { "${it.id}: ${it.name}" })
        val companyBox = ComboBox(FXCollections.observableArrayList(companyItems))
        if (u?.companyId != null) companies.find { it.id == u.companyId }?.let { companyBox.value = "${it.id}: ${it.name}" }
        else companyBox.selectionModel.selectFirst()

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 8.0; padding = Insets(16.0)
            var r = 0
            add(Label("ФИО *"), 0, r); add(nameF, 1, r); r++
            add(Label("Email *"), 0, r); add(emailF, 1, r); r++
            add(Label("Телефон"), 0, r); add(phoneF, 1, r); r++
            add(Label("Пароль" + if (u != null) " (опц.)" else " *"), 0, r); add(passF, 1, r); r++
            add(Label("Роль *"), 0, r); add(roleBox, 1, r); r++
            add(Label("Компания"), 0, r); add(companyBox, 1, r)
        }
        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.setResultConverter { btn ->
            if (btn == ButtonType.OK) {
                if (nameF.text.isBlank() || emailF.text.isBlank() || roleBox.value == null) {
                    Alert(Alert.AlertType.WARNING, "Заполните обязательные поля").showAndWait()
                    return@setResultConverter null
                }
                if (u == null && passF.text.isBlank()) {
                    Alert(Alert.AlertType.WARNING, "Укажите пароль для нового пользователя").showAndWait()
                    return@setResultConverter null
                }
                true
            } else null
        }

        dialog.showAndWait().ifPresent {
            Thread {
                try {
                    val roleId = roleBox.value?.split(":")?.firstOrNull()?.trim()?.toIntOrNull() ?: 5
                    val compVal = companyBox.value
                    val compId = if (compVal == null || compVal.startsWith("—")) null
                    else compVal.split(":").firstOrNull()?.trim()?.toIntOrNull()

                    if (u != null) {
                        UserDAO.update(u.id, emailF.text.trim(), phoneF.text.trim().ifBlank { null },
                            nameF.text.trim(), roleId, compId)
                    } else {
                        UserDAO.create(emailF.text.trim(), phoneF.text.trim().ifBlank { null },
                            nameF.text.trim(), passF.text, roleId, compId)
                    }
                    Platform.runLater { loadData() }
                } catch (e: Exception) {
                    Platform.runLater { Alert(Alert.AlertType.ERROR, "Ошибка: ${e.message}").showAndWait() }
                }
            }.start()
        }
    }

    private fun confirmDelete(u: User) {
        Alert(Alert.AlertType.CONFIRMATION, "Удалить пользователя «${u.fullName}»?").showAndWait().ifPresent {
            if (it == ButtonType.OK) {
                Thread {
                    try { UserDAO.delete(u.id); Platform.runLater { loadData() } }
                    catch (e: Exception) { Platform.runLater { Alert(Alert.AlertType.ERROR, e.message ?: "Ошибка").showAndWait() } }
                }.start()
            }
        }
    }
}
