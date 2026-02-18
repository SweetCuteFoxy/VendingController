package com.vending.ui

import com.vending.dao.NewsDAO
import com.vending.dao.SaleDAO
import com.vending.dao.VendingMachineDAO
import com.vending.model.DashboardStats
import com.vending.model.NewsItem
import com.vending.model.SalesByDay
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.chart.*
import javafx.scene.control.*
import javafx.scene.input.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

class DashboardView {
    val root: ScrollPane
    private val logger = LoggerFactory.getLogger(DashboardView::class.java)
    private val tilesContainer = FlowPane()
    private var stats = DashboardStats()
    private var salesData = listOf<SalesByDay>()
    private var news = listOf<NewsItem>()
    private val df = DecimalFormat("#,##0.00")

    // Tile visibility
    private val tileVisibility = mutableMapOf(
        "efficiency" to true,
        "status" to true,
        "summary" to true,
        "sales" to true,
        "news" to true
    )

    init {
        // Load data in background
        loadData()

        tilesContainer.apply {
            hgap = 16.0
            vgap = 16.0
            padding = Insets(24.0)
            prefWrapLength = 900.0
        }

        val refreshBar = HBox(12.0).apply {
            padding = Insets(16.0, 24.0, 0.0, 24.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("Главная панель").apply { font = Font.font(18.0); styleClass.add("page-title") },
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                Button("↻ Обновить данные").apply {
                    styleClass.add("primary-button")
                    setOnAction { loadData() }
                }
            )
        }

        val wrapper = VBox(refreshBar, tilesContainer)
        root = ScrollPane(wrapper).apply {
            isFitToWidth = true
            styleClass.add("dashboard-scroll")
        }

        buildTiles()
    }

    private fun loadData() {
        Thread {
            try {
                stats = VendingMachineDAO.getDashboardStats()
                salesData = SaleDAO.getSalesLast10Days()
                news = try { NewsDAO.findAll() } catch (_: Exception) { defaultNews() }
                if (news.isEmpty()) news = defaultNews()
                Platform.runLater { buildTiles() }
            } catch (e: Exception) {
                stats = DashboardStats()
                salesData = emptyList()
                news = defaultNews()
                Platform.runLater { buildTiles() }
            }
        }.start()
    }

    private fun defaultNews(): List<NewsItem> = listOf(
        NewsItem(1, "Обновление системы v2.5", "Внедрена система push-уведомлений для оперативного мониторинга состояния ТА.", java.time.LocalDateTime.now().minusDays(1)),
        NewsItem(2, "Расширение сети", "Подключены 5 новых торговых автоматов в регионах присутствия.", java.time.LocalDateTime.now().minusDays(3)),
        NewsItem(3, "Плановое обслуживание", "Напоминаем о необходимости проведения планового ТО до конца месяца.", java.time.LocalDateTime.now().minusDays(5))
    )

    private fun buildTiles() {
        tilesContainer.children.clear()

        // Context menu to toggle tiles
        val contextMenu = ContextMenu().apply {
            tileVisibility.keys.forEach { key ->
                val label = when (key) {
                    "efficiency" -> "Эффективность сети"
                    "status" -> "Состояние сети"
                    "summary" -> "Сводка"
                    "sales" -> "Динамика продаж"
                    "news" -> "Новости"
                    else -> key
                }
                items.add(CheckMenuItem(label).apply {
                    isSelected = tileVisibility[key] == true
                    setOnAction {
                        tileVisibility[key] = isSelected
                        buildTiles()
                    }
                })
            }
        }
        tilesContainer.setOnContextMenuRequested { e ->
            contextMenu.show(tilesContainer, e.screenX, e.screenY)
        }

        if (tileVisibility["efficiency"] == true) tilesContainer.children.add(buildEfficiencyTile())
        if (tileVisibility["status"] == true) tilesContainer.children.add(buildStatusTile())
        if (tileVisibility["summary"] == true) tilesContainer.children.add(buildSummaryTile())
        if (tileVisibility["sales"] == true) tilesContainer.children.add(buildSalesChartTile())
        if (tileVisibility["news"] == true) tilesContainer.children.add(buildNewsTile())

        // Make tiles draggable
        tilesContainer.children.forEach { makeDraggable(it) }
    }

    private fun buildEfficiencyTile(): Node {
        val pct = stats.efficiencyPercent
        val canvas = Canvas(140.0, 140.0)
        val gc = canvas.graphicsContext2D

        // Background circle
        gc.stroke = Color.web("#2a2e38")
        gc.lineWidth = 12.0
        gc.strokeArc(15.0, 15.0, 110.0, 110.0, 90.0, -360.0, javafx.scene.shape.ArcType.OPEN)

        // Progress arc
        val color = when {
            pct >= 80 -> Color.web("#50cd89")
            pct >= 50 -> Color.web("#ffc700")
            else -> Color.web("#f1416c")
        }
        gc.stroke = color
        gc.lineWidth = 12.0
        gc.strokeArc(15.0, 15.0, 110.0, 110.0, 90.0, -(360.0 * pct / 100.0), javafx.scene.shape.ArcType.OPEN)

        // Percentage text
        gc.fill = Color.web("#e8ecf1")
        gc.font = Font.font("System", javafx.scene.text.FontWeight.BOLD, 28.0)
        val text = "${pct.toInt()}%"
        val textWidth = text.length * 14.0
        gc.fillText(text, (140.0 - textWidth) / 2.0, 78.0)

        return createTileCard("Эффективность сети", VBox(10.0).apply {
            alignment = Pos.CENTER
            children.addAll(
                canvas,
                Label("${stats.workingMachines} из ${stats.totalMachines} работают").apply {
                    styleClass.add("tile-description")
                }
            )
        }, 300.0, 260.0)
    }

    private fun buildStatusTile(): Node {
        val pieData = FXCollections.observableArrayList(
            PieChart.Data("Работает (${stats.workingMachines})", stats.workingMachines.toDouble()),
            PieChart.Data("Не работает (${stats.brokenMachines})", stats.brokenMachines.toDouble()),
            PieChart.Data("На обслуживании (${stats.maintenanceMachines})", stats.maintenanceMachines.toDouble())
        )
        if (stats.offlineMachines > 0) {
            pieData.add(PieChart.Data("Офлайн (${stats.offlineMachines})", stats.offlineMachines.toDouble()))
        }

        val chart = PieChart(pieData).apply {
            isLegendVisible = true
            legendSide = Side.BOTTOM
            labelsVisible = false
            prefHeight = 200.0
            prefWidth = 280.0
        }

        // Color the slices
        Platform.runLater {
            chart.lookupAll(".chart-pie").forEachIndexed { i, node ->
                val color = when (i) {
                    0 -> "#50cd89"
                    1 -> "#f1416c"
                    2 -> "#ffc700"
                    3 -> "#7239ea"
                    else -> "#009ef7"
                }
                node.style = "-fx-pie-color: $color;"
            }
        }

        return createTileCard("Состояние сети", chart, 320.0, 300.0)
    }

    private fun buildSummaryTile(): Node {
        val grid = GridPane().apply {
            hgap = 16.0
            vgap = 12.0
            padding = Insets(8.0)
        }

        fun addRow(row: Int, icon: String, label: String, value: String) {
            grid.add(Label(icon).apply { font = Font.font(18.0) }, 0, row)
            grid.add(Label(label).apply { styleClass.add("summary-label") }, 1, row)
            grid.add(Label(value).apply { styleClass.add("summary-value") }, 2, row)
        }

        addRow(0, "💰", "Общий доход", "${df.format(stats.totalRevenue)} ₽")
        addRow(1, "💵", "Наличные в ТА", "${df.format(stats.totalCash)} ₽")
        addRow(2, "🛒", "Продаж сегодня", "${df.format(stats.totalSalesToday)} ₽ (${stats.totalSalesCount} шт.)")
        addRow(3, "🔧", "Заявки на ТО", "${stats.pendingServiceOrders} открыто")
        addRow(4, "✅", "Завершено ТО", "${stats.completedServiceOrders}")

        return createTileCard("Сводка", grid, 380.0, 260.0)
    }

    private fun buildSalesChartTile(): Node {
        val filterBox = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(0.0, 0.0, 8.0, 0.0)
        }

        val filterGroup = ToggleGroup()
        val amountBtn = RadioButton("По сумме").apply {
            toggleGroup = filterGroup; isSelected = true
            styleClass.add("chart-filter-btn")
        }
        val countBtn = RadioButton("По количеству").apply {
            toggleGroup = filterGroup
            styleClass.add("chart-filter-btn")
        }
        filterBox.children.addAll(Label("Фильтр:").apply { styleClass.add("chart-filter-label") }, amountBtn, countBtn)

        val xAxis = CategoryAxis().apply { label = "Дата" }
        val yAxis = NumberAxis().apply { label = "Сумма (₽)" }
        val chart = LineChart(xAxis, yAxis).apply {
            isLegendVisible = false
            prefHeight = 200.0
            prefWidth = 500.0
            animated = true
        }

        fun updateChart(byAmount: Boolean) {
            chart.data.clear()
            yAxis.label = if (byAmount) "Сумма (₽)" else "Количество"
            val series = XYChart.Series<String, Number>()
            series.name = if (byAmount) "Продажи" else "Кол-во"
            val fmt = DateTimeFormatter.ofPattern("dd.MM")
            salesData.forEach { s ->
                val value: Number = if (byAmount) s.totalAmount.toDouble() else s.totalCount
                series.data.add(XYChart.Data(s.date.format(fmt), value))
            }
            chart.data.add(series)
        }

        updateChart(true)
        filterGroup.selectedToggleProperty().addListener { _, _, new ->
            updateChart(new == amountBtn)
        }

        val content = VBox(8.0).apply {
            children.addAll(filterBox, chart)
        }

        return createTileCard("Динамика продаж за последние 10 дней", content, 560.0, 320.0)
    }

    private fun buildNewsTile(): Node {
        val newsList = VBox(8.0).apply { padding = Insets(4.0) }
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        news.take(5).forEach { item ->
            val card = VBox(4.0).apply {
                styleClass.add("news-item")
                padding = Insets(8.0, 12.0, 8.0, 12.0)
                children.addAll(
                    Label(item.title).apply { styleClass.add("news-title"); isWrapText = true },
                    Label(item.content).apply { styleClass.add("news-content"); isWrapText = true; maxWidth = 280.0 },
                    Label(item.createdAt?.format(fmt) ?: "").apply { styleClass.add("news-date") }
                )
            }
            newsList.children.add(card)
        }
        val scroll = ScrollPane(newsList).apply {
            isFitToWidth = true
            prefViewportHeight = 220.0
            styleClass.add("news-scroll")
        }
        return createTileCard("Новости", scroll, 340.0, 300.0)
    }

    private fun createTileCard(title: String, content: Node, prefW: Double, prefH: Double): VBox {
        return VBox(8.0).apply {
            styleClass.add("dashboard-tile")
            padding = Insets(16.0)
            prefWidth = prefW
            prefHeight = prefH
            children.addAll(
                Label(title).apply { styleClass.add("tile-title") },
                Separator(),
                content.apply { VBox.setVgrow(this, Priority.ALWAYS) }
            )
        }
    }

    // ========= Drag and Drop =========
    private var dragSource: Node? = null

    private fun makeDraggable(node: Node) {
        node.setOnDragDetected { event ->
            dragSource = node
            val db = node.startDragAndDrop(TransferMode.MOVE)
            val content = ClipboardContent()
            content.putString(tilesContainer.children.indexOf(node).toString())
            db.setContent(content)
            event.consume()
        }

        node.setOnDragOver { event ->
            if (event.gestureSource != node && event.dragboard.hasString()) {
                event.acceptTransferModes(TransferMode.MOVE)
            }
            event.consume()
        }

        node.setOnDragDropped { event ->
            val db = event.dragboard
            if (db.hasString()) {
                val sourceIndex = db.string.toIntOrNull() ?: return@setOnDragDropped
                val targetIndex = tilesContainer.children.indexOf(node)
                if (sourceIndex != targetIndex && sourceIndex >= 0 && targetIndex >= 0) {
                    val sourceNode = tilesContainer.children[sourceIndex]
                    tilesContainer.children.removeAt(sourceIndex)
                    tilesContainer.children.add(targetIndex, sourceNode)
                }
                event.isDropCompleted = true
            }
            event.consume()
        }

        node.setOnDragDone { it.consume() }
    }
}
