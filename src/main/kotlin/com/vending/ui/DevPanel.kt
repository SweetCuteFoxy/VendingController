package com.vending.ui

import com.vending.database.DatabaseConfig
import javafx.animation.FadeTransition
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.text.Font
import javafx.util.Duration
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DbStats(
    val machines: Int = 0,
    val working: Int = 0,
    val broken: Int = 0,
    val maintenance: Int = 0,
    val sales: Int = 0,
    val salesToday: Int = 0,
    val revenueToday: Double = 0.0,
    val users: Int = 0,
    val orders: Int = 0,
    val openOrders: Int = 0,
    val history: Int = 0,
    val companies: Int = 0,
    val modems: Int = 0
)

/**
 * Dev panel - full-featured version
 * Ctrl+Shift+D to toggle.
 */
class DevPanel {
    private val logger = LoggerFactory.getLogger(DevPanel::class.java)
    private var overlay: StackPane? = null
    private var isVisible = false
    private var autoRefreshTimeline: Timeline? = null

    companion object {
        val HOTKEY: KeyCodeCombination = KeyCodeCombination(
            KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN
        )
    }

    fun toggle(parent: StackPane) { if (isVisible) hide() else show(parent) }

    private fun show(parent: StackPane) {
        val panel = buildPanel()
        overlay = StackPane().apply {
            styleClass.add("dev-overlay")
            children.add(panel)
            StackPane.setAlignment(panel, Pos.CENTER_RIGHT)
            setOnMouseClicked { e -> if (e.target == this) hide() }
        }
        parent.children.add(overlay)
        isVisible = true
        FadeTransition(Duration.millis(220.0), overlay).apply { fromValue = 0.0; toValue = 1.0; play() }
    }

    fun hide() {
        val ov = overlay ?: return
        autoRefreshTimeline?.stop(); autoRefreshTimeline = null
        FadeTransition(Duration.millis(160.0), ov).apply {
            fromValue = 1.0; toValue = 0.0
            setOnFinished { (ov.parent as? Pane)?.children?.remove(ov) }
            play()
        }
        isVisible = false; overlay = null
    }

    private fun buildPanel(): VBox {
        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            styleClass.add("dev-tab-pane")
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        tabPane.tabs.addAll(
            Tab("Статистика", buildStatsTab()),
            Tab("SQL-консоль", buildSqlTab()),
            Tab("Система", buildSysTab())
        )
        return VBox(0.0).apply {
            styleClass.add("dev-panel")
            prefWidth = 580.0; maxWidth = 580.0; maxHeight = Double.MAX_VALUE
            val dot = Circle(5.0, Color.web("#50cd89"))
            val title = Label("Панель разработчика").apply {
                font = Font.font(15.0); styleClass.add("dev-panel-title")
            }
            val themeToggle = CheckBox("Светлая тема").apply {
                styleClass.add("dev-theme-toggle"); isSelected = ThemeManager.isLight
                selectedProperty().addListener { _, _, v -> ThemeManager.isLight = v }
            }
            val closeBtn = Button("x").apply {
                styleClass.add("dev-close-btn"); setOnAction { hide() }
            }
            val header = HBox(10.0).apply {
                styleClass.add("dev-panel-header"); padding = Insets(14.0, 16.0, 14.0, 20.0)
                alignment = Pos.CENTER_LEFT
                children.addAll(dot, title, Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }, themeToggle, closeBtn)
            }
            children.addAll(header, Separator(), tabPane)
        }
    }

    //  STATS TAB 
    private fun buildStatsTab(): ScrollPane {
        val content = VBox(16.0).apply { padding = Insets(20.0); styleClass.add("dev-tab-content") }

        data class StatCard(val icon: String, val label: String, val key: String)
        val cards = listOf(
            StatCard("Robot", "Автоматов", "machines"),
            StatCard("OK", "Работают", "working"),
            StatCard("X", "Не работают", "broken"),
            StatCard("Wrench", "На ТО", "maintenance"),
            StatCard("Money", "Продаж всего", "sales"),
            StatCard("Cal", "Продаж сегодня", "salesToday"),
            StatCard("User", "Пользователей", "users"),
            StatCard("Clip", "Заявок всего", "orders"),
            StatCard("Open", "Открытых заявок", "openOrders"),
            StatCard("Scroll", "Истории ТО", "history"),
            StatCard("Bldg", "Компаний", "companies"),
            StatCard("Modem", "Модемов", "modems")
        )

        val valueLabels = mutableMapOf<String, Label>()
        val grid = GridPane().apply { hgap = 12.0; vgap = 12.0 }
        cards.forEachIndexed { idx, card ->
            val col = idx % 3; val row = idx / 3
            val lbl = Label("-").apply { styleClass.add("dev-stat-value") }
            valueLabels[card.key] = lbl
            val cell = VBox(4.0).apply {
                styleClass.add("dev-stat-card"); padding = Insets(10.0, 14.0, 10.0, 14.0); alignment = Pos.CENTER_LEFT
                children.addAll(lbl, Label(card.label).apply { styleClass.add("dev-stat-label") })
            }
            grid.add(cell, col, row)
        }

        val revLabel = Label("-").apply { styleClass.add("dev-revenue-value") }
        val revCard = HBox(12.0).apply {
            styleClass.add("dev-revenue-card"); padding = Insets(12.0, 16.0, 12.0, 16.0); alignment = Pos.CENTER_LEFT
            children.addAll(VBox(2.0).apply {
                children.addAll(revLabel, Label("Выручка за сегодня").apply { styleClass.add("dev-stat-label") })
            })
        }

        val lastRefreshLabel = Label("Не обновлялось").apply { styleClass.add("dev-hint") }
        val autoCheck = CheckBox("Авто-обновление (30с)").apply { styleClass.add("dev-theme-toggle") }
        val refreshBtn = Button("Обновить").apply { styleClass.add("primary-button") }

        fun loadStats() {
            Thread {
                try {
                    val s = fetchStats()
                    Platform.runLater {
                        valueLabels["machines"]?.text = s.machines.toString()
                        valueLabels["working"]?.text = s.working.toString()
                        valueLabels["broken"]?.text = s.broken.toString()
                        valueLabels["maintenance"]?.text = s.maintenance.toString()
                        valueLabels["sales"]?.text = s.sales.toString()
                        valueLabels["salesToday"]?.text = s.salesToday.toString()
                        valueLabels["users"]?.text = s.users.toString()
                        valueLabels["orders"]?.text = s.orders.toString()
                        valueLabels["openOrders"]?.text = s.openOrders.toString()
                        valueLabels["history"]?.text = s.history.toString()
                        valueLabels["companies"]?.text = s.companies.toString()
                        valueLabels["modems"]?.text = s.modems.toString()
                        revLabel.text = "%.2f R".format(s.revenueToday)
                        lastRefreshLabel.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    }
                } catch (e: Exception) {
                    Platform.runLater { lastRefreshLabel.text = "Ошибка: ${e.message}" }
                }
            }.start()
        }

        refreshBtn.setOnAction { loadStats() }
        autoCheck.selectedProperty().addListener { _, _, v ->
            if (v) {
                autoRefreshTimeline = Timeline(KeyFrame(Duration.seconds(30.0), { loadStats() })).apply {
                    cycleCount = Timeline.INDEFINITE; play()
                }
            } else { autoRefreshTimeline?.stop(); autoRefreshTimeline = null }
        }

        val ctrlRow = HBox(12.0).apply {
            alignment = Pos.CENTER_LEFT
            children.addAll(refreshBtn, autoCheck, Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }, lastRefreshLabel)
        }
        content.children.addAll(grid, revCard, ctrlRow)
        loadStats()
        return ScrollPane(content).apply { isFitToWidth = true; styleClass.add("dev-scroll") }
    }

    private fun fetchStats(): DbStats = transaction {
        fun qi(sql: String) = exec(sql) { rs: ResultSet -> if (rs.next()) rs.getInt(1) else 0 } ?: 0
        fun qd(sql: String) = exec(sql) { rs: ResultSet -> if (rs.next()) rs.getDouble(1) else 0.0 } ?: 0.0
        DbStats(
            machines     = qi("SELECT COUNT(*) FROM vending_machines"),
            working      = qi("SELECT COUNT(*) FROM vending_machines WHERE status='working'"),
            broken       = qi("SELECT COUNT(*) FROM vending_machines WHERE status='broken'"),
            maintenance  = qi("SELECT COUNT(*) FROM vending_machines WHERE status='maintenance'"),
            sales        = qi("SELECT COUNT(*) FROM sales"),
            salesToday   = qi("SELECT COUNT(*) FROM sales WHERE DATE(sale_time)=CURRENT_DATE"),
            revenueToday = qd("SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE DATE(sale_time)=CURRENT_DATE"),
            users        = qi("SELECT COUNT(*) FROM users"),
            orders       = qi("SELECT COUNT(*) FROM service_orders"),
            openOrders   = qi("SELECT COUNT(*) FROM service_orders WHERE status NOT IN ('completed','cancelled')"),
            history      = qi("SELECT COUNT(*) FROM service_history"),
            companies    = qi("SELECT COUNT(*) FROM companies"),
            modems       = qi("SELECT COUNT(*) FROM modems")
        )
    }

    //  SQL CONSOLE TAB 
    private fun buildSqlTab(): VBox {
        val content = VBox(10.0).apply {
            padding = Insets(16.0); styleClass.add("dev-tab-content"); VBox.setVgrow(this, Priority.ALWAYS)
        }
        val queryArea = TextArea().apply {
            promptText = "SELECT * FROM vending_machines LIMIT 10;"; prefRowCount = 5
            styleClass.add("dev-sql-area"); font = Font.font("Monospaced", 12.0); isWrapText = false
        }

        data class QQ(val lbl: String, val sql: String)
        val qqList = listOf(
            QQ("Автоматы",    "SELECT id, name, status, total_revenue FROM vending_machines ORDER BY id LIMIT 20;"),
            QQ("Топ ТА",      "SELECT vm.name, SUM(s.total_amount) revenue FROM sales s JOIN vending_machines vm ON s.machine_id=vm.id GROUP BY vm.name ORDER BY revenue DESC LIMIT 10;"),
            QQ("Offline",     "SELECT id, name, status FROM vending_machines WHERE status!='working';"),
            QQ("Заявки",      "SELECT order_number, type, status, priority FROM service_orders WHERE status NOT IN ('completed','cancelled') ORDER BY priority DESC;"),
            QQ("Польз.",      "SELECT u.id, u.full_name, u.email, r.name role FROM users u JOIN roles r ON u.role_id=r.id ORDER BY u.id;"),
            QQ("Прод.сег.",   "SELECT vm.name, COUNT(*) cnt, SUM(s.total_amount) total FROM sales s JOIN vending_machines vm ON s.machine_id=vm.id WHERE DATE(s.sale_time)=CURRENT_DATE GROUP BY vm.name ORDER BY total DESC;"),
            QQ("Таблицы",     "SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name;")
        )
        val chips = FlowPane(6.0, 6.0).apply {
            qqList.forEach { q -> children.add(Button(q.lbl).apply {
                styleClass.add("dev-chip-btn"); setOnAction { queryArea.text = q.sql }
            }) }
        }

        val resultTable = TableView<List<String>>().apply {
            placeholder = Label("Введите SQL-запрос выше и нажмите Выполнить")
            styleClass.add("dev-result-table"); VBox.setVgrow(this, Priority.ALWAYS)
        }
        val statusLbl = Label("").apply { styleClass.add("dev-sql-status") }

        val execBtn = Button("Выполнить").apply { styleClass.add("primary-button") }
        val clearBtn = Button("Очистить").apply { styleClass.add("secondary-button") }

        execBtn.setOnAction {
            val sql = queryArea.text.trim().trimEnd(';')
            if (sql.isBlank()) return@setOnAction
            statusLbl.text = "Выполняется..."; statusLbl.style = ""
            resultTable.columns.clear(); resultTable.items = FXCollections.observableArrayList()
            Thread {
                try {
                    val headers = mutableListOf<String>(); val rows = mutableListOf<List<String>>()
                    transaction {
                        exec(sql) { rs: ResultSet ->
                            val meta = rs.metaData; val cc = meta.columnCount
                            headers.addAll((1..cc).map { meta.getColumnLabel(it) })
                            while (rs.next()) rows.add((1..cc).map { rs.getString(it) ?: "NULL" })
                        }
                    }
                    Platform.runLater {
                        resultTable.columns.clear()
                        headers.forEachIndexed { i, h ->
                            resultTable.columns.add(TableColumn<List<String>, String>(h).apply {
                                setCellValueFactory { d -> SimpleStringProperty(d.value.getOrNull(i) ?: "") }
                                prefWidth = 120.0
                            })
                        }
                        resultTable.items = FXCollections.observableArrayList(rows)
                        statusLbl.text = "${rows.size} строк | ${headers.size} столбцов"
                        statusLbl.style = "-fx-text-fill: #50cd89;"
                    }
                } catch (e: Exception) {
                    Platform.runLater {
                        statusLbl.text = "Ошибка: ${e.message}"
                        statusLbl.style = "-fx-text-fill: #ef6b6b;"
                    }
                }
            }.start()
        }
        clearBtn.setOnAction {
            queryArea.clear(); resultTable.columns.clear()
            resultTable.items = FXCollections.observableArrayList(); statusLbl.text = ""
        }

        val btnRow = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            children.addAll(execBtn, clearBtn, Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }, statusLbl)
        }
        content.children.addAll(
            Label("Быстрые запросы:").apply { styleClass.add("dev-section-title") }, chips, Separator(),
            Label("SQL-запрос:").apply { styleClass.add("dev-section-title") }, queryArea, btnRow, resultTable
        )
        VBox.setVgrow(resultTable, Priority.ALWAYS)
        return content
    }

    //  SYSTEM INFO TAB 
    private fun buildSysTab(): ScrollPane {
        val content = VBox(12.0).apply { padding = Insets(20.0); styleClass.add("dev-tab-content") }
        val rt = Runtime.getRuntime(); val mb = 1024 * 1024
        val totalMem = rt.totalMemory() / mb; val freeMem = rt.freeMemory() / mb
        val usedMem = totalMem - freeMem; val maxMem = rt.maxMemory() / mb

        val dbUrl = try {
            val p = java.util.Properties()
            val s = DatabaseConfig::class.java.classLoader.getResourceAsStream("database.properties")
            if (s != null) { p.load(s); p.getProperty("db.url", "N/A") } else "N/A"
        } catch (_: Exception) { "N/A" }

        fun row(k: String, v: String) = HBox(8.0).apply {
            children.addAll(
                Label(k).apply { styleClass.add("dev-info-key"); minWidth = 170.0 },
                Label(v).apply { styleClass.add("dev-info-val"); isWrapText = true }
            )
        }
        fun section(title: String, rows: List<HBox>) = VBox(4.0).apply {
            styleClass.add("dev-info-section"); padding = Insets(12.0, 16.0, 12.0, 16.0)
            children.add(Label(title).apply { styleClass.add("dev-section-title") })
            children.add(Separator().apply { padding = Insets(4.0, 0.0, 4.0, 0.0) })
            children.addAll(rows)
        }

        val memBar = ProgressBar(usedMem.toDouble() / maxMem.coerceAtLeast(1).toDouble()).apply {
            prefWidth = Double.MAX_VALUE
            val pct = usedMem.toDouble() / maxMem.coerceAtLeast(1).toDouble()
            style = "-fx-accent: ${if (pct > 0.85) "#f1416c" else if (pct > 0.6) "#ffc700" else "#50cd89"};"
        }
        val gcBtn = Button("GC").apply {
            styleClass.add("secondary-button")
            setOnAction { System.gc(); statusAlert("Garbage collector запущен вручную") }
        }

        content.children.addAll(
            section("JVM / ОС", listOf(
                row("Java", System.getProperty("java.version")),
                row("JVM", System.getProperty("java.vm.name")),
                row("Поставщик", System.getProperty("java.vendor")),
                row("ОС", "${System.getProperty("os.name")} ${System.getProperty("os.version")}"),
                row("Архитектура", System.getProperty("os.arch")),
                row("Процессоры", rt.availableProcessors().toString())
            )),
            section("Память JVM", listOf(
                row("Использовано", "$usedMem МБ"),
                row("Выделено", "$totalMem МБ"),
                row("Максимум", "$maxMem МБ"),
                row("Свободно", "$freeMem МБ")
            )),
            Label("Использование памяти: $usedMem / $maxMem МБ").apply { styleClass.add("dev-hint") },
            memBar, gcBtn,
            section("Приложение", listOf(
                row("JavaFX", System.getProperty("javafx.runtime.version") ?: "21.x"),
                row("Kotlin", KotlinVersion.CURRENT.toString()),
                row("DB URL", dbUrl),
                row("Hotkey Dev", "Ctrl+Shift+D"),
                row("Hotkey Theme", "Ctrl+Shift+T")
            ))
        )
        return ScrollPane(content).apply { isFitToWidth = true; styleClass.add("dev-scroll") }
    }

    private fun statusAlert(msg: String) {
        Alert(Alert.AlertType.INFORMATION, msg).showAndWait()
    }
}
