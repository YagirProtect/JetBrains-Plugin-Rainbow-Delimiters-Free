package dev.yaro.rainbowbraces.palette

import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Modal editor for one raw RGB value. The old/new preview is local to this dialog;
 * the settings page applies its saturation/brightness preview after confirmation.
 * Returning null on Cancel leaves the palette draft unchanged.
 */
internal object PaletteColorChooser {
    fun choose(parent: JComponent, initial: Color): Color? {
        var selected: Color? = null
        val chooser = JColorChooser(initial).apply {
            chooserPanels = chooserPanels.filter { panel ->
                val name = panel.displayName.lowercase()
                name.contains("hsv") || name.contains("hsl") || name.contains("rgb")
            }.toTypedArray()
            previewPanel = createColorPreviewPanel(initial, this)
        }
        selectRgbPanel(chooser)
        val dialog = JColorChooser.createDialog(
            parent,
            "Choose Color",
            true,
            chooser,
            { selected = chooser.color },
            null
        )
        dialog.isVisible = true
        return selected
    }

    private fun createColorPreviewPanel(initial: Color, chooser: JColorChooser): JPanel {
        val oldColor = ColorSwatch(initial).apply {
            preferredSize = Dimension(JBUI.scale(36), JBUI.scale(36))
            minimumSize = preferredSize
            toolTipText = "Old color"
        }
        val newColor = ColorSwatch(initial).apply {
            preferredSize = Dimension(JBUI.scale(36), JBUI.scale(36))
            minimumSize = preferredSize
            toolTipText = "New color"
        }
        chooser.selectionModel.addChangeListener {
            newColor.color = chooser.color
            newColor.repaint()
        }
        return JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), JBUI.scale(4))).apply {
            add(oldColor)
            add(newColor)
        }
    }

    private fun selectRgbPanel(component: JComponent) {
        // Swing look-and-feel implementations may nest the chooser tabs differently.
        // Search defensively and retain the chooser's default if RGB is unavailable.
        val tabbedPane = findTabbedPane(component) ?: return
        for (index in 0 until tabbedPane.tabCount) {
            if (tabbedPane.getTitleAt(index).equals("rgb", ignoreCase = true)) {
                tabbedPane.selectedIndex = index
                return
            }
        }
    }

    private fun findTabbedPane(component: JComponent): JTabbedPane? {
        if (component is JTabbedPane) return component
        component.components.forEach { child ->
            if (child is JTabbedPane) return child
            if (child is JComponent) {
                val nested = findTabbedPane(child)
                if (nested != null) return nested
            }
        }
        return null
    }

}

/** Paints its current display color; callers own source RGB values and repaint scheduling. */
internal class ColorSwatch(var color: Color) : JBPanel<ColorSwatch>() {
    init {
        border = BorderFactory.createLineBorder(Color(0x6E6E6E))
        isOpaque = false
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val graphics = g.create() as Graphics2D
        try {
            graphics.color = color
            graphics.fillRect(1, 1, width - 2, height - 2)
        } finally {
            graphics.dispose()
        }
    }
}

/** Matches color-cell sizing and scales the add glyph with the IDE's UI scale. */
internal class AddColorSwatch : JBPanel<AddColorSwatch>() {
    init {
        border = BorderFactory.createLineBorder(Color(0x6E6E6E))
        isOpaque = false
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val graphics = g.create() as Graphics2D
        try {
            graphics.color = foreground
            graphics.font = graphics.font.deriveFont(Font.BOLD, JBUI.scale(16).toFloat())
            val text = "+"
            val metrics = graphics.fontMetrics
            val x = (width - metrics.stringWidth(text)) / 2
            val y = (height - metrics.height) / 2 + metrics.ascent
            graphics.drawString(text, x, y)
        } finally {
            graphics.dispose()
        }
    }
}

internal class SimpleDocumentListener(private val onChange: () -> Unit) : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) = onChange()
    override fun removeUpdate(e: DocumentEvent) = onChange()
    override fun changedUpdate(e: DocumentEvent) = onChange()
}
