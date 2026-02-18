package com.vending.ui

import com.vending.service.AuthService
import com.vending.service.NotificationService
import com.vending.ui.admin.*
import javafx.animation.TranslateTransition
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.stage.Stage
import javafx.util.Duration

class MainView(private val stage: Stage) {
    val root: BorderPane = BorderPane()

    private val contentArea = StackPane()
    private val sidebarBox = VBox()
    private var isSidebarCollapsed = false
    private val SIDEBAR_WIDTH = 260.0
    private val SIDEBAR_COLLAPSED_WIDTH = 60.0

    // Views cache
    private var dashboardView: DashboardView? = null
    private var machineMonitorView: MachineMonitorView? = null
    private var reportsView: ReportsView? = null
    private var inventoryView: InventoryView? = null
    private var vendingMachinesView: VendingMachinesView? = null
    private var companiesView: CompaniesView? = null
    private var usersView: UsersView? = null
    private var modemsView: ModemsView? = null
    private var serviceOrdersView: ServiceOrdersView? = null

    // Active menu tracking
    private var activeMenuBtn: Button? = null
    private val adminSubMenu = VBox(2.0)
    private var adminExpanded = false

    init {
        root.styleClass.add("main-root")
        buildSidebar()
        buildTopBar()
        buildContent()
        buildNotificationLayer()
        navigateTo("dashboard")
    }

    private fun buildTopBar() {
        val topBar = HBox(10.0).apply {
            styleClass.add("top-bar")
            padding = Insets(8.0, 20.0, 8.0, 20.0)
            alignment = Pos.CENTER_LEFT
        }

        val toggleBtn = Button("☰").apply {
            styleClass.add("sidebar-toggle")
            setOnAction { toggleSidebar() }
        }

        val breadcrumb = Label("Главная").apply {
            styleClass.add("breadcrumb")
            id = "breadcrumb"
        }

        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

        // Notification bell
        val bellBtn = Button("🔔").apply {
            styleClass.add("icon-button")
            setOnAction {
                NotificationService.pushInfo("Тест", "Тестовое уведомление системы")
            }
        }

        // User profile
        val user = AuthService.getCurrentUser()
        val role = AuthService.getCurrentRole()
        val userBox = HBox(8.0).apply {
            alignment = Pos.CENTER
            val avatar = Label("👤").apply { font = Font.font(20.0) }
            val nameLabel = Label(AuthService.getUserShortName()).apply {
                styleClass.add("top-user-name")
            }
            val roleLabel = Label(role?.name ?: "").apply {
                styleClass.add("top-user-role")
            }
            val infoBox = VBox(2.0).apply {
                children.addAll(nameLabel, roleLabel)
            }
            children.addAll(avatar, infoBox)
        }

        // Dropdown for user
        val menuButton = MenuButton("▼").apply {
            styleClass.add("user-menu-button")
            items.addAll(
                MenuItem("Мой профиль").apply {
                    setOnAction { showProfile() }
                },
                SeparatorMenuItem(),
                MenuItem("Выход").apply {
                    setOnAction { VendingApp.instance.logout() }
                }
            )
        }

        topBar.children.addAll(toggleBtn, breadcrumb, spacer, bellBtn, userBox, menuButton)
        root.top = topBar
    }

    private fun buildSidebar() {
        sidebarBox.apply {
            styleClass.add("sidebar")
            prefWidth = SIDEBAR_WIDTH
            minWidth = SIDEBAR_WIDTH
            maxWidth = SIDEBAR_WIDTH
            padding = Insets(0.0)
        }

        // Brand
        val brand = HBox(10.0).apply {
            styleClass.add("sidebar-brand")
            padding = Insets(20.0, 16.0, 20.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("🏭").apply { font = Font.font(24.0) },
                Label("VendControl").apply { styleClass.add("sidebar-brand-text") }
            )
        }

        // User card in sidebar
        val user = AuthService.getCurrentUser()
        val role = AuthService.getCurrentRole()
        val userCard = VBox(4.0).apply {
            styleClass.add("sidebar-user-card")
            padding = Insets(12.0, 16.0, 12.0, 16.0)
            alignment = Pos.CENTER_LEFT
            children.addAll(
                Label("👤  ${AuthService.getUserShortName()}").apply { styleClass.add("sidebar-user-name") },
                Label(role?.name ?: "").apply { styleClass.add("sidebar-user-role") }
            )
        }

        val sep = Separator().apply { padding = Insets(4.0, 0.0, 4.0, 0.0) }

        // Menu items
        val menuContainer = VBox(2.0).apply {
            padding = Insets(8.0, 8.0, 8.0, 8.0)
        }

        val dashboardBtn = createMenuButton("🏠", "Главная", "dashboard")
        val monitorBtn = createMenuButton("📊", "Монитор ТА", "monitor")
        val reportsBtn = createMenuButton("📋", "Детальные отчёты", "reports")
        val inventoryBtn = createMenuButton("📦", "Учёт ТМЦ", "inventory")

        // Admin section with sub-menu
        val adminBtn = Button().apply {
            styleClass.add("sidebar-menu-button")
            maxWidth = Double.MAX_VALUE
            alignment = Pos.CENTER_LEFT
            graphic = HBox(10.0).apply {
                alignment = Pos.CENTER_LEFT
                val arrow = Label(if (adminExpanded) "▼" else "▶").apply {
                    styleClass.add("menu-arrow")
                    id = "admin-arrow"
                    minWidth = 12.0
                }
                children.addAll(
                    Label("⚙").apply { minWidth = 20.0 },
                    Label("Администрирование").apply { styleClass.add("sidebar-menu-text") },
                    Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                    arrow
                )
                HBox.setHgrow(this, Priority.ALWAYS)
            }
            setOnAction { toggleAdminMenu() }
        }

        adminSubMenu.apply {
            padding = Insets(0.0, 0.0, 0.0, 20.0)
            isManaged = false
            isVisible = false
        }
        val subMachines = createSubMenuButton("Торговые автоматы", "admin-machines")
        val subCompanies = createSubMenuButton("Компании", "admin-companies")
        val subUsers = createSubMenuButton("Пользователи", "admin-users")
        val subModems = createSubMenuButton("Модемы", "admin-modems")
        val subExtra = createSubMenuButton("Дополнительные", "admin-extra")
        adminSubMenu.children.addAll(subMachines, subCompanies, subUsers, subModems, subExtra)

        menuContainer.children.addAll(
            dashboardBtn, monitorBtn, reportsBtn, inventoryBtn,
            adminBtn, adminSubMenu
        )

        val scrollPane = ScrollPane(menuContainer).apply {
            isFitToWidth = true
            styleClass.add("sidebar-scroll")
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        sidebarBox.children.addAll(brand, userCard, sep, scrollPane)
        root.left = sidebarBox
    }

    private fun createMenuButton(icon: String, text: String, target: String): Button {
        return Button().apply {
            styleClass.add("sidebar-menu-button")
            maxWidth = Double.MAX_VALUE
            alignment = Pos.CENTER_LEFT
            graphic = HBox(10.0).apply {
                alignment = Pos.CENTER_LEFT
                children.addAll(
                    Label(icon).apply { minWidth = 20.0 },
                    Label(text).apply { styleClass.add("sidebar-menu-text") }
                )
            }
            setOnAction {
                setActiveMenu(this)
                navigateTo(target)
            }
        }
    }

    private fun createSubMenuButton(text: String, target: String): Button {
        return Button("• $text").apply {
            styleClass.add("sidebar-submenu-button")
            maxWidth = Double.MAX_VALUE
            alignment = Pos.CENTER_LEFT
            setOnAction {
                setActiveMenu(this)
                navigateTo(target)
            }
        }
    }

    private fun setActiveMenu(btn: Button) {
        activeMenuBtn?.styleClass?.remove("active")
        btn.styleClass.add("active")
        activeMenuBtn = btn
    }

    private fun toggleAdminMenu() {
        adminExpanded = !adminExpanded
        adminSubMenu.isVisible = adminExpanded
        adminSubMenu.isManaged = adminExpanded
        val arrow = root.lookup("#admin-arrow") as? Label
        arrow?.text = if (adminExpanded) "▼" else "▶"
    }

    private fun toggleSidebar() {
        isSidebarCollapsed = !isSidebarCollapsed
        val tt = TranslateTransition(Duration.millis(200.0), sidebarBox)
        if (isSidebarCollapsed) {
            sidebarBox.prefWidth = SIDEBAR_COLLAPSED_WIDTH
            sidebarBox.minWidth = SIDEBAR_COLLAPSED_WIDTH
            sidebarBox.maxWidth = SIDEBAR_COLLAPSED_WIDTH
            // Hide text labels
            sidebarBox.lookupAll(".sidebar-menu-text").forEach { (it as? Label)?.isVisible = false; (it as? Label)?.isManaged = false }
            sidebarBox.lookupAll(".sidebar-brand-text").forEach { (it as? Label)?.isVisible = false; (it as? Label)?.isManaged = false }
            sidebarBox.lookupAll(".sidebar-user-card").forEach { it.isVisible = false; it.isManaged = false }
            sidebarBox.lookupAll(".menu-arrow").forEach { it.isVisible = false; it.isManaged = false }
        } else {
            sidebarBox.prefWidth = SIDEBAR_WIDTH
            sidebarBox.minWidth = SIDEBAR_WIDTH
            sidebarBox.maxWidth = SIDEBAR_WIDTH
            sidebarBox.lookupAll(".sidebar-menu-text").forEach { (it as? Label)?.isVisible = true; (it as? Label)?.isManaged = true }
            sidebarBox.lookupAll(".sidebar-brand-text").forEach { (it as? Label)?.isVisible = true; (it as? Label)?.isManaged = true }
            sidebarBox.lookupAll(".sidebar-user-card").forEach { it.isVisible = true; it.isManaged = true }
            sidebarBox.lookupAll(".menu-arrow").forEach { it.isVisible = true; it.isManaged = true }
        }
    }

    private fun buildContent() {
        contentArea.apply {
            styleClass.add("content-area")
            padding = Insets(0.0)
        }
        root.center = contentArea
    }

    private fun buildNotificationLayer() {
        val toastBox = VBox(8.0).apply {
            alignment = Pos.TOP_RIGHT
            padding = Insets(16.0)
            isPickOnBounds = false
            maxWidth = 400.0
            maxHeight = Region.USE_PREF_SIZE
        }
        StackPane.setAlignment(toastBox, Pos.TOP_RIGHT)
        NotificationService.setToastContainer(toastBox)

        // Overlay
        val overlay = StackPane(contentArea, toastBox).apply {
            StackPane.setAlignment(toastBox, Pos.TOP_RIGHT)
        }
        root.center = overlay
    }

    private fun navigateTo(target: String) {
        val (view, title) = when (target) {
            "dashboard" -> {
                if (dashboardView == null) dashboardView = DashboardView()
                dashboardView!!.root to "Главная"
            }
            "monitor" -> {
                if (machineMonitorView == null) machineMonitorView = MachineMonitorView()
                machineMonitorView!!.root to "Монитор ТА"
            }
            "reports" -> {
                if (reportsView == null) reportsView = ReportsView()
                reportsView!!.root to "Детальные отчёты"
            }
            "inventory" -> {
                if (inventoryView == null) inventoryView = InventoryView()
                inventoryView!!.root to "Учёт ТМЦ"
            }
            "admin-machines" -> {
                if (vendingMachinesView == null) vendingMachinesView = VendingMachinesView(stage)
                vendingMachinesView!!.root to "Администрирование → Торговые автоматы"
            }
            "admin-companies" -> {
                if (companiesView == null) companiesView = CompaniesView()
                companiesView!!.root to "Администрирование → Компании"
            }
            "admin-users" -> {
                if (usersView == null) usersView = UsersView()
                usersView!!.root to "Администрирование → Пользователи"
            }
            "admin-modems" -> {
                if (modemsView == null) modemsView = ModemsView()
                modemsView!!.root to "Администрирование → Модемы"
            }
            "admin-extra" -> {
                if (serviceOrdersView == null) serviceOrdersView = ServiceOrdersView()
                serviceOrdersView!!.root to "Администрирование → Дополнительные"
            }
            else -> {
                createPlaceholder("Не найдено", "Страница не найдена") to target
            }
        }

        contentArea.children.clear()
        contentArea.children.add(view)

        // Update breadcrumb
        val breadcrumb = root.lookup("#breadcrumb") as? Label
        breadcrumb?.text = title
    }

    private fun createPlaceholder(title: String, desc: String): Node {
        return VBox(16.0).apply {
            alignment = Pos.CENTER
            padding = Insets(40.0)
            children.addAll(
                Label(title).apply { styleClass.add("page-title"); font = Font.font(24.0) },
                Label(desc).apply { styleClass.add("page-subtitle") },
                Label("🚧 В разработке").apply { font = Font.font(18.0); opacity = 0.5 }
            )
        }
    }

    private fun showProfile() {
        val user = AuthService.getCurrentUser() ?: return
        val role = AuthService.getCurrentRole()
        val dialog = Alert(Alert.AlertType.INFORMATION).apply {
            title = "Профиль пользователя"
            headerText = user.fullName
            contentText = buildString {
                appendLine("Email: ${user.email}")
                appendLine("Телефон: ${user.phone ?: "—"}")
                appendLine("Роль: ${role?.name ?: "—"}")
                appendLine("Компания: ${user.companyName.ifBlank { "—" }}")
                appendLine("Статус: ${if (user.isActive) "Активен" else "Неактивен"}")
            }
        }
        dialog.showAndWait()
    }
}
