package com.vending.ui

import com.vending.service.AuthService
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*

class LoginView(private val onSuccess: () -> Unit) {

    val root: StackPane = StackPane()

    init {
        root.styleClass.add("login-root")
        root.alignment = Pos.CENTER

        val card = VBox(20.0).apply {
            styleClass.add("login-card")
            alignment = Pos.CENTER
            padding = Insets(40.0, 50.0, 40.0, 50.0)
            maxWidth = 440.0
            maxHeight = 560.0
        }

        // Logo/Title
        val logoLabel = VendingIcon.create(56.0)
        val titleLabel = Label("Vending Controller").apply {
            styleClass.add("login-title")
        }
        val subtitleLabel = Label("Система управления сетью торговых автоматов").apply {
            styleClass.add("login-subtitle")
        }

        // Form fields
        val emailField = TextField().apply {
            promptText = "Email"
            styleClass.add("login-field")
            maxWidth = 320.0
        }
        val passwordField = PasswordField().apply {
            promptText = "Пароль"
            styleClass.add("login-field")
            maxWidth = 320.0
        }

        val errorLabel = Label().apply {
            styleClass.add("login-error")
            isVisible = false
            isManaged = false
        }

        val loginButton = Button("Войти").apply {
            styleClass.add("login-button")
            maxWidth = 320.0
            isDefaultButton = true
        }

        val spinner = ProgressIndicator().apply {
            prefWidth = 24.0
            prefHeight = 24.0
            isVisible = false
            isManaged = false
        }

        loginButton.setOnAction {
            val email = emailField.text.trim()
            val password = passwordField.text

            if (email.isEmpty() || password.isEmpty()) {
                errorLabel.text = "Заполните все поля"
                errorLabel.isVisible = true
                errorLabel.isManaged = true
                return@setOnAction
            }

            loginButton.isDisable = true
            spinner.isVisible = true
            spinner.isManaged = true
            errorLabel.isVisible = false
            errorLabel.isManaged = false

            Thread {
                val success = AuthService.login(email, password)
                javafx.application.Platform.runLater {
                    loginButton.isDisable = false
                    spinner.isVisible = false
                    spinner.isManaged = false
                    if (success) {
                        onSuccess()
                    } else {
                        errorLabel.text = "Неверный email или пароль"
                        errorLabel.isVisible = true
                        errorLabel.isManaged = true
                        passwordField.clear()
                    }
                }
            }.start()
        }

        // Also login on Enter in password field
        passwordField.setOnAction { loginButton.fire() }

        val hintLabel = Label("Демо: admin@system.ru / password123").apply {
            styleClass.add("login-hint")
        }

        card.children.addAll(
            logoLabel, titleLabel, subtitleLabel,
            Region().apply { prefHeight = 10.0 },
            emailField, passwordField, errorLabel,
            spinner, loginButton,
            Region().apply { prefHeight = 5.0 },
            hintLabel
        )

        root.children.add(card)
    }
}
