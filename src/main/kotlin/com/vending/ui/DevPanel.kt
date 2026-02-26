package com.vending.ui

import com.vending.dao.*
import com.vending.database.Tables
import com.vending.service.AuthService
import javafx.animation.FadeTransition
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.util.Duration
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * РџР°РЅРµР»СЊ СЂР°Р·СЂР°Р±РѕС‚С‡РёРєР° вЂ” СЃРєСЂС‹С‚Р°СЏ СѓС‚РёР»РёС‚Р° РґР»СЏ РѕС‚Р»Р°РґРєРё Рё СѓРїСЂР°РІР»РµРЅРёСЏ РґР°РЅРЅС‹РјРё.
 * РћС‚РєСЂС‹РІР°РµС‚СЃСЏ РїРѕ Ctrl+Shift+D.
 */
class DevPanel {
    private val logger = LoggerFactory.getLogger(DevPanel::class.java)
    private var overlay: StackPane? = null
    private var isVisible = false
    private val dtFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val df = DecimalFormat("#,##0.##")

    companion object {
        val HOTKEY: KeyCodeCombination = KeyCodeCombination(
            KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN
        )
    }

    fun toggle(parent: StackPane) {
        if (isVisible) hide() else show(parent)
    }

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
        FadeTransition(Duration.millis(200.0), overlay).apply { fromValue = 0.0; toValue = 1.0; play() }
    }

    fun hide() {
        val ov = overlay ?: return
        FadeTransition(Duration.millis(150.0), ov).apply {
            fromValue = 1.0; toValue = 0.0
            setOnFinished { (ov.parent as? Pane)?.children?.remove(ov) }
            play()
        }
        isVisible = false
        overlay = null
    }

    // ================================================================
    // MAIN PANEL
    // ================================================================

    private fun buildPanel(): VBox {
        val statusLabel = Label().apply {
            styleClass.add("dev-status")
            isVisible = false; isManaged = false; isWrapText = true
        }

        fun showStatus(msg: String, success: Boolean) {
            statusLabel.text = msg
            statusLabel.style = if (success) "-fx-text-fill: #50cd89;" else "-fx-text-fill: #ef6b6b;"
            statusLabel.isVisible = true; statusLabel.isManaged = true
        }

        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            styleClass.add("dev-tab-pane")
        }

        tabPane.tabs.addAll(
            Tab("вљЎ Р”РµР№СЃС‚РІРёСЏ", buildActionsTab(::showStatus)),
            Tab("рџ“Љ Р‘Р”", buildDbStatsTab()),
            Tab("рџ’ѕ РЎРёСЃС‚РµРјР°", buildSystemTab()),
            Tab("рџ“‹ Р›РѕРіРё", buildLogsTab())
        )

        return VBox(12.0).apply {
            styleClass.add("dev-panel")
            padding = Insets(20.0)
            prefWidth = 520.0
            maxWidth = 520.0
            maxHeight = Double.MAX_VALUE
            children.addAll(buildHeader(), Separator(), tabPane, statusLabel)
        }
    }

    private fun buildHeader(): HBox = HBox(10.0).apply {
        alignment = Pos.CENTER_LEFT
        children.addAll(
            Label("рџ›  Dev Panel").apply { font = Font.font(16.0); styleClass.add("dev-panel-title") },
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            Label(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).apply { styleClass.add("dev-hint") },
            Button("вњ•").apply { styleClass.add("dev-close-btn"); setOnAction { hide() } }
        )
    }

    // ================================================================
    // TAB 1: QUICK ACTIONS
    // ================================================================

    private fun buildActionsTab(showStatus: (String, Boolean) -> Unit): ScrollPane {
        val themeToggle = CheckBox("вЂ  РЎРІРµС‚Р»Р°СЏ С‚РµРјР°").apply {
            styleClass.add("dev-theme-toggle")
            isSelected = ThemeManager.isLight
            selectedProperty().addListener { _, _, v -> ThemeManager.isLight = v }
        }

        val machineIdField = devField("ID Р°РІС‚РѕРјР°С‚Р°", 90.0, "1")
        val productIdField = devField("ID РїСЂРѕРґСѓРєС‚Р°", 90.0, "1")
        val qtyField = devField("РљРѕР»-РІРѕ", 60.0, "1")
        val priceField = devField("Р¦РµРЅР°", 70.0, "150")
        val methodCombo = ComboBox<String>().apply { items.addAll("card", "cash", "qr"); value = "card"; prefWidth = 85.0 }

        val addSaleBtn = Button("вћ• Р”РѕР±Р°РІРёС‚СЊ РїСЂРѕРґР°Р¶Сѓ").apply {
            styleClass.addAll("primary-button", "dev-action-btn")
            setOnAction {
                val mId = machineIdField.text.trim().toIntOrNull()
                val pId = productIdField.text.trim().toIntOrNull()
                val qty = qtyField.text.trim().toIntOrNull() ?: 1
                val price = priceField.text.trim().toDoubleOrNull() ?: 150.0
                if (mId == null || pId == null) { showStatus("вљ  РЈРєР°Р¶РёС‚Рµ ID Р°РІС‚РѕРјР°С‚Р° Рё РїСЂРѕРґСѓРєС‚Р°", false); return@setOnAction }
                Thread {
                    try {
                        SaleDAO.create(mId, pId, qty, BigDecimal.valueOf(price), methodCombo.value ?: "card")
                        Platform.runLater { showStatus("вњ“ РџСЂРѕРґР°Р¶Р° РґРѕР±Р°РІР»РµРЅР° (РўРђ=$mId, РџСЂРѕРґ=$pId, Г—$qty)", true) }
                    } catch (e: Exception) { Platform.runLater { showStatus("вњ— ${e.message}", false) } }
                }.start()
            }
        }

        val bulkCountField = devField("РљРѕР»-РІРѕ", 70.0, "20")
        val bulkBtn = Button("рџЋІ РЎР»СѓС‡Р°Р№РЅС‹Рµ РїСЂРѕРґР°Р¶Рё").apply {
            style = "-fx-background-color: #2d3748; -fx-text-fill: #f0c060; -fx-border-color: #f0c060; -fx-border-radius:6; -fx-background-radius:6; -fx-padding:6 14; -fx-cursor:hand;"
            setOnAction {
                val count = bulkCountField.text.trim().toIntOrNull() ?: 20
                Thread {
                    try {
                        val machines = VendingMachineDAO.findAll()
                        val products = try { ProductDAO.findAll() } catch (_: Exception) { emptyList() }
                        if (machines.isEmpty() || products.isEmpty()) {
                            Platform.runLater { showStatus("вљ  РќРµС‚ РјР°С€РёРЅ/РїСЂРѕРґСѓРєС‚РѕРІ РІ Р‘Р”", false) }; return@Thread
                        }
                        val methods = listOf("card","cash","qr")
                        var added = 0
                        repeat(count) {
                            runCatching {
                                SaleDAO.create(machines.random().id, products.random().id,
                                    (1..3).random(), BigDecimal.valueOf((50..500).random().toDouble()), methods.random())
                                added++
                            }
                        }
                        Platform.runLater { showStatus("вњ“ Р”РѕР±Р°РІР»РµРЅРѕ $added РїСЂРѕРґР°Р¶", true) }
                    } catch (e: Exception) { Platform.runLater { showStatus("вњ— ${e.message}", false) } }
                }.start()
            }
        }

        val statusMachineIdField = devField("ID Р°РІС‚РѕРјР°С‚Р°", 90.0)
        val statusCombo = ComboBox<String>().apply { items.addAll("working","broken","maintenance","offline"); value = "working"; prefWidth = 130.0 }
        val changeStatusBtn = Button("вџі РР·РјРµРЅРёС‚СЊ СЃС‚Р°С‚СѓСЃ").apply {
            styleClass.addAll("primary-button", "dev-action-btn")
            setOnAction {
                val mId = statusMachineIdField.text.trim().toIntOrNull()
                if (mId == null) { showStatus("вљ  РЈРєР°Р¶РёС‚Рµ ID Р°РІС‚РѕРјР°С‚Р°", false); return@setOnAction }
                Thread {
                    try {
                        VendingMachineDAO.updateStatus(mId, statusCombo.value)
                        Platform.runLater { showStatus("вњ“ РЎС‚Р°С‚СѓСЃ РўРђ #$mId в†’ ${statusCombo.value}", true) }
                    } catch (e: Exception) { Platform.runLater { showStatus("вњ— ${e.message}", false) } }
                }.start()
            }
        }

        val shortcuts = listOf("Ctrl+Shift+D" to "РћС‚РєСЂС‹С‚СЊ / Р·Р°РєСЂС‹С‚СЊ РїР°РЅРµР»СЊ", "Ctrl+Shift+T" to "РџРµСЂРµРєР»СЋС‡РёС‚СЊ С‚РµРјСѓ", "Esc" to "Р—Р°РєСЂС‹С‚СЊ")
        val shortcutsBox = VBox(6.0).apply {
            children.add(Label("Р“РѕСЂСЏС‡РёРµ РєР»Р°РІРёС€Рё").apply { styleClass.add("dev-section-title") })
            shortcuts.forEach { (k, d) ->
                children.add(HBox(8.0).apply {
                    alignment = Pos.CENTER_LEFT
                    children.addAll(
                        Label(k).apply { style = "-fx-background-color:#252a36;-fx-text-fill:#5b8def;-fx-padding:2 8;-fx-background-radius:4;-fx-font-size:11px;-fx-font-family:monospace;" },
                        Label(d).apply { styleClass.add("dev-hint") }
                    )
                })
            }
        }

        val content = VBox(16.0).apply {
            padding = Insets(14.0, 4.0, 14.0, 4.0)
            children.addAll(
                themeToggle, Separator(),
                devSection("вљЎ Р‘С‹СЃС‚СЂР°СЏ РїСЂРѕРґР°Р¶Р°",
                    HBox(6.0).apply { alignment = Pos.CENTER_LEFT; children.addAll(machineIdField, productIdField, qtyField, priceField, methodCombo) },
                    addSaleBtn),
                devSection("рџЋІ Р“РµРЅРµСЂР°С†РёСЏ РїСЂРѕРґР°Р¶",
                    HBox(8.0).apply { alignment = Pos.CENTER_LEFT; children.addAll(Label("РљРѕР»РёС‡РµСЃС‚РІРѕ:").apply { styleClass.add("dev-hint") }, bulkCountField) },
                    bulkBtn),
                devSection("рџ”§ РЎС‚Р°С‚СѓСЃ Р°РІС‚РѕРјР°С‚Р°",
                    HBox(6.0).apply { alignment = Pos.CENTER_LEFT; children.addAll(statusMachineIdField, statusCombo) },
                    changeStatusBtn),
                Separator(), shortcutsBox
            )
        }
        return ScrollPane(content).apply {
            isFitToWidth = true; styleClass.add("dev-scroll")
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED; hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    // ================================================================
    // TAB 2: DB STATISTICS
    // ================================================================

    private fun buildDbStatsTab(): BorderPane {
        val tableNames = listOf(
            "companies" to "РљРѕРјРїР°РЅРёРё", "users" to "РџРѕР»СЊР·РѕРІР°С‚РµР»Рё",
            "vending_machines" to "РўРѕСЂРіРѕРІС‹Рµ Р°РІС‚РѕРјР°С‚С‹", "modems" to "РњРѕРґРµРјС‹",
            "products" to "РўРѕРІР°СЂС‹", "sales" to "РџСЂРѕРґР°Р¶Рё",
            "service_orders" to "Р—Р°СЏРІРєРё РЅР° РўРћ", "service_history" to "РСЃС‚РѕСЂРёСЏ РўРћ",
            "notifications" to "РЈРІРµРґРѕРјР»РµРЅРёСЏ", "news" to "РќРѕРІРѕСЃС‚Рё"
        )
        val statsGrid = GridPane().apply { hgap = 24.0; vgap = 10.0; padding = Insets(14.0, 4.0, 14.0, 4.0) }
        val countLabels = mutableMapOf<String, Label>()
        tableNames.forEachIndexed { idx, (tbl, title) ->
            val cntLabel = Label("вЂ¦").apply { style = "-fx-text-fill:#5b8def;-fx-font-size:14px;-fx-font-weight:bold;" }
            countLabels[tbl] = cntLabel
            statsGrid.add(Label(title).apply { styleClass.add("dev-hint"); prefWidth = 180.0 }, 0, idx)
            statsGrid.add(cntLabel, 1, idx)
        }

        val refreshBtn = Button("в†» РћР±РЅРѕРІРёС‚СЊ").apply {
            styleClass.add("primary-button")
            setOnAction { refreshDbStats(countLabels) }
        }

        val topBar = HBox(10.0).apply {
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("рџџў РЎС‚Р°С‚РёСЃС‚РёРєР° РїРѕ С‚Р°Р±Р»РёС†Р°Рј Р‘Р”").apply { styleClass.add("dev-section-title") },
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }, refreshBtn
            )
        }
        refreshDbStats(countLabels)
        val content = VBox(12.0).apply {
            padding = Insets(14.0, 4.0, 14.0, 4.0)
            children.addAll(topBar, statsGrid)
        }
        return BorderPane(ScrollPane(content).apply {
            isFitToWidth = true; vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER; styleClass.add("dev-scroll")
        })
    }

    private fun refreshDbStats(labels: Map<String, Label>) {
        Thread {
            val counts = mutableMapOf<String, Long>()
            runCatching {
                transaction {
                    counts["companies"] = Tables.Companies.selectAll().count()
                    counts["users"] = Tables.Users.selectAll().count()
                    counts["vending_machines"] = Tables.VendingMachines.selectAll().count()
                    counts["modems"] = Tables.Modems.selectAll().count()
                    counts["products"] = runCatching { Tables.Products.selectAll().count() }.getOrDefault(-1L)
                    counts["sales"] = runCatching { Tables.Sales.selectAll().count() }.getOrDefault(-1L)
                    counts["service_orders"] = runCatching { Tables.ServiceOrders.selectAll().count() }.getOrDefault(-1L)
                    counts["service_history"] = runCatching { Tables.ServiceHistory.selectAll().count() }.getOrDefault(-1L)
                    counts["notifications"] = runCatching { Tables.Notifications.selectAll().count() }.getOrDefault(-1L)
                    counts["news"] = runCatching { Tables.News.selectAll().count() }.getOrDefault(-1L)
                }
            }.onFailure { e -> logger.error("DevPanel db stats error", e) }
            Platform.runLater { labels.forEach { (tbl, lbl) -> lbl.text = counts[tbl]?.let { if (it >= 0) it.toString() else "вЂ”" } ?: "вЂ”" } }
        }.start()
    }

    // ================================================================
    // TAB 3: SYSTEM
    // ================================================================

    private fun buildSystemTab(): ScrollPane {
        val rt = Runtime.getRuntime()
        val mb = 1024.0 * 1024.0
        val usedLabel = Label().apply { style = "-fx-text-fill:#5b8def;-fx-font-weight:bold;-fx-font-size:13px;" }
        val totalLabel = Label().apply { styleClass.add("dev-hint") }
        val maxLabel = Label().apply { styleClass.add("dev-hint") }
        val memBar = ProgressBar(0.0).apply { prefWidth = 300.0; prefHeight = 10.0 }

        fun updateMem() {
            val used = (rt.totalMemory() - rt.freeMemory()) / mb
            val max = rt.maxMemory() / mb
            val pct = used / max
            usedLabel.text = "Р—Р°РЅСЏС‚Рѕ: ${df.format(used)} РњР‘ (${(pct * 100).toInt()}%)"
            totalLabel.text = "Р’С‹РґРµР»РµРЅРѕ JVM: ${df.format(rt.totalMemory() / mb)} РњР‘"
            maxLabel.text = "РњР°РєСЃРёРјСѓРј JVM: ${df.format(max)} РњР‘"
            memBar.progress = pct
            memBar.style = if (pct > 0.85) "-fx-accent:#ef6b6b;" else if (pct > 0.65) "-fx-accent:#ffc700;" else "-fx-accent:#50cd89;"
        }
        updateMem()
        Timeline(KeyFrame(Duration.seconds(2.0), { updateMem() })).apply { cycleCount = Timeline.INDEFINITE; play() }

        val gcBtn = Button("рџ—‘ Р—Р°РїСѓСЃС‚РёС‚СЊ GC").apply {
            style = "-fx-background-color:#252a36;-fx-text-fill:#c8cdd8;-fx-border-color:#333844;-fx-border-radius:6;-fx-background-radius:6;-fx-padding:5 14;-fx-cursor:hand;"
            setOnAction { System.gc(); updateMem() }
        }

        val sysInfo = VBox(6.0).apply {
            children.addAll(
                infoRow("в• Java", "${System.getProperty("java.version")} (${System.getProperty("java.vendor") ?: "?"})"),
                infoRow("рџ’» РћРЎ", "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"),
                infoRow("рџ”ў CPU СЏРґСЂР°", rt.availableProcessors().toString()),
                infoRow("рџ‘¤ РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ", try { AuthService.getCurrentUser()?.fullName ?: "вЂ”" } catch (_: Exception) { "вЂ”" }),
                infoRow("рџ•ђ Р’СЂРµРјСЏ", LocalDateTime.now().format(dtFmt))
            )
        }

        val content = VBox(16.0).apply {
            padding = Insets(14.0, 4.0, 14.0, 4.0)
            children.addAll(
                devSection("рџ’ѕ РџР°РјСЏС‚СЊ", VBox(6.0).apply { children.addAll(usedLabel, memBar, totalLabel, maxLabel) }, gcBtn),
                Separator(),
                Label("рџ–Ґ РЎРёСЃС‚РµРјР°").apply { styleClass.add("dev-section-title") },
                sysInfo
            )
        }
        return ScrollPane(content).apply {
            isFitToWidth = true; vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER; styleClass.add("dev-scroll")
        }
    }

    // ================================================================
    // TAB 4: LOGS
    // ================================================================

    private fun buildLogsTab(): BorderPane {
        val logArea = TextArea().apply {
            isEditable = false
            style = "-fx-background-color:#0b0e14;-fx-text-fill:#9ecb6e;-fx-font-family:'Consolas',monospace;-fx-font-size:11px;"
            prefHeight = 340.0; text = "Р—Р°РіСЂСѓР·РєР°вЂ¦"
        }
        val linesCombo = ComboBox<Int>().apply { items.addAll(30, 100, 200, 500); value = 100 }
        val filterField = TextField().apply { promptText = "Р¤РёР»СЊС‚СЂ: WARN, ERRORвЂ¦"; styleClass.add("dev-field"); prefWidth = 180.0 }

        fun loadLogs() {
            Thread {
                val logFile = File("logs/vending-controller.log")
                val text = if (logFile.exists()) runCatching {
                    val lines = logFile.readLines()
                    val flt = filterField.text.trim()
                    (if (flt.isEmpty()) lines else lines.filter { it.contains(flt, true) }).takeLast(linesCombo.value).joinToString("\n")
                }.getOrElse { "РћС€РёР±РєР° С‡С‚РµРЅРёСЏ Р»РѕРіР°: ${it.message}" }
                else "Р¤Р°Р№Р» Р»РѕРіР° РЅРµ РЅР°Р№РґРµРЅ: logs/vending-controller.log"
                Platform.runLater { logArea.text = text; logArea.scrollTop = Double.MAX_VALUE }
            }.start()
        }

        filterField.setOnAction { loadLogs() }
        linesCombo.valueProperty().addListener { _, _, _ -> loadLogs() }
        loadLogs()

        val topBar = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT; padding = Insets(8.0, 4.0, 8.0, 4.0)
            children.addAll(
                Label("РЎС‚СЂРѕРє:").apply { styleClass.add("dev-hint") }, linesCombo,
                Label("Р¤РёР»СЊС‚СЂ:").apply { styleClass.add("dev-hint") }, filterField,
                Button("вњ•").apply { styleClass.add("dev-close-btn"); setOnAction { filterField.clear(); loadLogs() } },
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                Button("в†»").apply { styleClass.add("primary-button"); setOnAction { loadLogs() } }
            )
        }

        return BorderPane().apply { top = topBar; center = logArea; padding = Insets(6.0, 4.0, 4.0, 4.0) }
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private fun devField(prompt: String, width: Double = 90.0, default: String = ""): TextField =
        TextField(default).apply { promptText = prompt; prefWidth = width; styleClass.add("dev-field") }

    private fun devSection(title: String, vararg content: javafx.scene.Node): VBox = VBox(8.0).apply {
        children.add(Label(title).apply { styleClass.add("dev-section-title") })
        children.addAll(content)
    }

    private fun infoRow(label: String, value: String): HBox = HBox(10.0).apply {
        alignment = Pos.CENTER_LEFT
        children.addAll(
            Label(label).apply { prefWidth = 120.0; styleClass.add("dev-hint") },
            Label(value).apply { style = "-fx-text-fill:#c8cdd8;-fx-font-size:12px;"; isWrapText = true }
        )
    }
}

