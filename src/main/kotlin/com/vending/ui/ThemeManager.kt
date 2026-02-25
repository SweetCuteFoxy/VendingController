package com.vending.ui

import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.Scene

/**
 * Управление тёмной/светлой темой.
 * Хранит текущее состояние и переключает CSS-класс "light-theme" на .root.
 */
object ThemeManager {
    /** true = light theme, false = dark (default) */
    val lightThemeProperty = SimpleBooleanProperty(false)
    var isLight: Boolean
        get() = lightThemeProperty.get()
        set(v) = lightThemeProperty.set(v)

    private val scenes = mutableSetOf<Scene>()

    fun register(scene: Scene) {
        scenes.add(scene)
        applyTo(scene)
        lightThemeProperty.addListener { _, _, _ -> applyTo(scene) }
    }

    fun toggle() {
        isLight = !isLight
    }

    private fun applyTo(scene: Scene) {
        val root = scene.root ?: return
        if (isLight) {
            if (!root.styleClass.contains("light-theme")) root.styleClass.add("light-theme")
        } else {
            root.styleClass.remove("light-theme")
        }
    }
}
