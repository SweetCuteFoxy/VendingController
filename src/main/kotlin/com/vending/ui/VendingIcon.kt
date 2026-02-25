package com.vending.ui

import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.shape.StrokeLineJoin

/**
 * Векторная иконка вендингового автомата, нарисованная через JavaFX Shape.
 * Используется как логотип приложения на экране входа и в боковом меню.
 */
object VendingIcon {

    /**
     * Создаёт иконку вендингового автомата заданного размера (по высоте).
     * @param size высота иконки в пикселях
     * @return Group с фигурами
     */
    fun create(size: Double): Group {
        val w = size * 0.68
        val h = size
        val r = size * 0.07

        // --- Корпус автомата ---
        val body = Rectangle(w, h).apply {
            arcWidth = r * 2; arcHeight = r * 2
            fill = Color.web("#3d8bcd")
            stroke = Color.web("#2b6ca3")
            strokeWidth = size * 0.02
            strokeLineJoin = StrokeLineJoin.ROUND
        }

        // --- Верхняя панель (бренд-полоса) ---
        val topPanel = Rectangle(w * 0.78, h * 0.10).apply {
            x = w * 0.11; y = h * 0.055
            arcWidth = r * 0.8; arcHeight = r * 0.8
            fill = Color.web("#1d5a8a")
        }

        // --- Витринное окно с товарами ---
        val glass = Rectangle(w * 0.48, h * 0.38).apply {
            x = w * 0.10; y = h * 0.20
            arcWidth = r * 0.6; arcHeight = r * 0.6
            fill = Color.web("#e3f2fd")
            stroke = Color.web("#1d5a8a")
            strokeWidth = size * 0.015
        }

        // Горизонтальные полки внутри витрины
        val shelf1 = Line(w * 0.10, h * 0.33, w * 0.58, h * 0.33).apply {
            stroke = Color.web("#90caf9"); strokeWidth = size * 0.012
        }
        val shelf2 = Line(w * 0.10, h * 0.46, w * 0.58, h * 0.46).apply {
            stroke = Color.web("#90caf9"); strokeWidth = size * 0.012
        }

        // --- Кнопки выбора товара (правая колонка) ---
        val bx = w * 0.74
        val bs = size * 0.04
        val btn1 = Circle(bs).apply { centerX = bx; centerY = h * 0.28; fill = Color.web("#ef5350") }
        val btn2 = Circle(bs).apply { centerX = bx; centerY = h * 0.37; fill = Color.web("#66bb6a") }
        val btn3 = Circle(bs).apply { centerX = bx; centerY = h * 0.46; fill = Color.web("#ffa726") }

        // --- Купюроприёмник (правая сторона, ниже кнопок) ---
        val coinSlot = Rectangle(w * 0.08, h * 0.06).apply {
            x = w * 0.70; y = h * 0.56
            arcWidth = r * 0.4; arcHeight = r * 0.4
            fill = Color.web("#ffd54f")
            stroke = Color.web("#f9a825")
            strokeWidth = size * 0.01
        }

        // --- Отсек выдачи (нижняя ниша) ---
        val dispenserOuter = Rectangle(w * 0.60, h * 0.10).apply {
            x = w * 0.20; y = h * 0.72
            arcWidth = r; arcHeight = r
            fill = Color.web("#163d5c")
        }
        val dispenserInner = Rectangle(w * 0.52, h * 0.06).apply {
            x = w * 0.24; y = h * 0.74
            arcWidth = r * 0.5; arcHeight = r * 0.5
            fill = Color.web("#0d2b42")
        }

        // --- Ножки ---
        val legW = w * 0.06
        val legH = h * 0.04
        val legLeft = Rectangle(legW, legH).apply {
            x = w * 0.12; y = h * 0.96
            fill = Color.web("#2b6ca3")
        }
        val legRight = Rectangle(legW, legH).apply {
            x = w - w * 0.12 - legW; y = h * 0.96
            fill = Color.web("#2b6ca3")
        }

        return Group(
            body, topPanel,
            glass, shelf1, shelf2,
            btn1, btn2, btn3,
            coinSlot,
            dispenserOuter, dispenserInner,
            legLeft, legRight
        )
    }
}
