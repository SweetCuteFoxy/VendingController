package com.vending.ui

import com.vending.database.DatabaseConfig
import com.vending.service.AuthService
import com.vending.service.NotificationService
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.stage.Stage
import org.slf4j.LoggerFactory

class VendingApp : Application() {
    private val logger = LoggerFactory.getLogger(VendingApp::class.java)
    private lateinit var primaryStage: Stage

    companion object {
        lateinit var instance: VendingApp
            private set
    }

    override fun start(stage: Stage) {
        instance = this
        primaryStage = stage
        primaryStage.title = "Vending Controller — Система управления сетью ТА"
        primaryStage.isMaximized = false
        primaryStage.width = 1280.0
        primaryStage.height = 800.0
        primaryStage.minWidth = 1024.0
        primaryStage.minHeight = 700.0

        // Initialize database
        try {
            DatabaseConfig.init()
            logger.info("Database initialized")
        } catch (e: Exception) {
            logger.error("Database initialization failed", e)
        }

        showLogin()
        primaryStage.show()
    }

    fun showLogin() {
        val loginView = LoginView { onLoginSuccess() }
        val scene = Scene(loginView.root, 1280.0, 800.0)
        scene.stylesheets.add(javaClass.getResource("/style.css")?.toExternalForm() ?: "")
        primaryStage.scene = scene
    }

    private fun onLoginSuccess() {
        showMain()
        NotificationService.startSimulation()
    }

    fun showMain() {
        val mainView = MainView(primaryStage)
        val scene = Scene(mainView.root, 1280.0, 800.0)
        scene.stylesheets.add(javaClass.getResource("/style.css")?.toExternalForm() ?: "")
        ThemeManager.register(scene)

        // Theme toggle: Ctrl+Shift+T
        val themeHotkey = KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)
        scene.accelerators[themeHotkey] = Runnable { ThemeManager.toggle() }

        // Dev panel: Ctrl+Shift+D
        scene.accelerators[DevPanel.HOTKEY] = Runnable { mainView.toggleDevPanel() }

        primaryStage.scene = scene
        primaryStage.isMaximized = true
    }

    fun logout() {
        AuthService.logout()
        showLogin()
    }

    fun getStage(): Stage = primaryStage
}
