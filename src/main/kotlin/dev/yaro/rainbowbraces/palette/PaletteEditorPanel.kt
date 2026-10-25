package dev.yaro.rainbowbraces.palette

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Swing presentation and file-dialog boundary for the settings-page palette draft.
 * The model owns edits; this panel owns widgets and local preview parameters only.
 * Each swatch retains the original light/dark RGB values so slider updates always
 * transform the source, rather than repeatedly transforming an adjusted color.
 */
internal class PaletteEditorPanel(private val editorModel: PaletteEditorModel) : JPanel(BorderLayout()) {
    private data class SwatchPreview(val swatch: ColorSwatch, val light: Int, val dark: Int)

    private val swatches = mutableListOf<SwatchPreview>()
    private var saturation = 100
    private var brightness = 100
    private val palettesPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    init {
        add(JBScrollPane(JPanel(BorderLayout()).apply { add(palettesPanel, BorderLayout.NORTH) }), BorderLayout.CENTER)
        render()
    }

    fun setColorAdjustment(saturation: Int, brightness: Int) {
        // Repaint all palette rows in place while dragging a slider. Rebuilding the
        // component tree here would interrupt focus and allocate unnecessary widgets.
        this.saturation = saturation.coerceIn(0, 100)
        this.brightness = brightness.coerceIn(0, 100)
        swatches.forEach { preview ->
            preview.swatch.color = previewColor(preview.light, preview.dark)
            preview.swatch.repaint()
        }
    }

    private fun previewColor(light: Int, dark: Int): Color = JBColor(
        PaletteColorAdjustment.adjust(light, saturation, brightness),
        PaletteColorAdjustment.adjust(dark, saturation, brightness)
    )

    fun duplicateActivePalette() {
        editorModel.duplicateActive()
        render()
    }

    fun exportPalettes() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export Palettes"
            fileFilter = FileNameExtensionFilter("JSON files", "json")
            selectedFile = File("rainbow-delimiters-palettes.json")
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile
        val file = if (selected.extension.equals("json", ignoreCase = true)) selected else File(selected.parentFile, "${selected.name}.json")
        try {
            file.writeText(PaletteJsonCodec.encode(editorModel.activePaletteId, editorModel.palettes), Charsets.UTF_8)
        } catch (e: java.io.IOException) {
            JOptionPane.showMessageDialog(this, "Could not export palettes: ${e.message}", "Export Palettes", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun importPalettes() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Import Palettes"
            fileFilter = FileNameExtensionFilter("JSON files", "json")
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        try {
            val imported = PaletteJsonCodec.decode(chooser.selectedFile.readText(Charsets.UTF_8))
            if (imported.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No palettes found in selected file.", "Import Palettes", JOptionPane.WARNING_MESSAGE)
                return
            }
            editorModel.importPalettes(imported)
            render()
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Could not import palettes: ${e.message}", "Import Palettes", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun render() {
        // Structural edits recreate rows and their preview bindings together. Clear
        // old bindings so later slider changes cannot retain detached components.
        val target = palettesPanel
        target.removeAll()
        swatches.clear()

        editorModel.palettes.forEachIndexed { index, palette ->
            if (index > 0) {
                target.add(JSeparator().apply {
                    maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(1))
                })
            }
            target.add(createPaletteRow(palette))
        }

        target.revalidate()
        target.repaint()
    }

    private fun createPaletteRow(palette: RainbowPalette): JPanel {
        val row = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            border = JBUI.Borders.emptyBottom(4)
        }

        val header = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)))
        val colorsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        header.add(JRadioButton().apply {
            isSelected = palette.id == editorModel.activePaletteId
            addActionListener {
                editorModel.select(palette.id)
                render()
            }
        })

        if (palette.isBuiltIn()) {
            header.add(JBLabel(palette.name).apply {
                preferredSize = Dimension(JBUI.scale(150), preferredSize.height)
            })
        } else {
            header.add(JTextField(palette.name).apply {
                preferredSize = Dimension(JBUI.scale(150), preferredSize.height)
                toolTipText = "Rename palette"
                document.addDocumentListener(SimpleDocumentListener {
                    editorModel.rename(palette.id, text)
                })
            })
        }

        palette.lightColors.chunked(8).forEachIndexed { rowIndex, colors ->
            colorsPanel.add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
                colors.forEachIndexed { colorIndexInRow, rgb ->
                    val colorIndex = rowIndex * 8 + colorIndexInRow
                    add(createColorSwatch(palette, colorIndex, rgb))
                }
                if (rowIndex == palette.lightColors.lastIndex / 8) {
                    add(createAddColorSwatch(palette))
                }
            })
        }

        if (palette.isBuiltIn()) {
            header.add(JButton("Reset").apply {
                addActionListener {
                    editorModel.resetBuiltIn(palette.id)
                    render()
                }
            })
            header.add(JButton("-").apply {
                toolTipText = "Built-in palettes cannot be deleted"
                isEnabled = false
            })
        } else {
            header.add(JButton("-").apply {
                toolTipText = "Delete palette"
                addActionListener {
                    editorModel.delete(palette.id)
                    render()
                }
            })
        }

        row.add(header, BorderLayout.NORTH)
        row.add(colorsPanel, BorderLayout.CENTER)
        return row
    }

    private fun createAddColorSwatch(palette: RainbowPalette): JComponent =
        AddColorSwatch().apply {
            preferredSize = Dimension(JBUI.scale(24), JBUI.scale(24))
            minimumSize = preferredSize
            maximumSize = preferredSize
            toolTipText = "Add color"
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(event: java.awt.event.MouseEvent) {
                    if (!javax.swing.SwingUtilities.isLeftMouseButton(event)) return
                    event.consume()
                    editorModel.addColor(palette.id)
                    // Finish this mouse dispatch before replacing its component tree.
                    javax.swing.SwingUtilities.invokeLater { render() }
                }
            })
        }

    private fun createColorSwatch(palette: RainbowPalette, colorIndex: Int, rgb: Int): JComponent =
        ColorSwatch(previewColor(rgb, palette.darkColors[colorIndex])).apply {
            swatches.add(SwatchPreview(this, rgb, palette.darkColors[colorIndex]))
            preferredSize = Dimension(JBUI.scale(24), JBUI.scale(24))
            minimumSize = preferredSize
            maximumSize = preferredSize
            toolTipText = "Edit color; preview includes saturation and brightness adjustments"
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(event: java.awt.event.MouseEvent) {
                    if (event.isPopupTrigger || javax.swing.SwingUtilities.isRightMouseButton(event)) {
                        showColorMenu(this@apply, palette, colorIndex, event.x, event.y)
                        return
                    }
                    // The picker edits the raw source RGB, not the adjusted swatch.
                    // This prevents lowering brightness twice after confirming a color.
                    val chosen = PaletteColorChooser.choose(this@apply, Color(palette.lightColors[colorIndex]))
                        ?: return
                    val normalized = chosen.rgb and 0xFFFFFF
                    editorModel.setColor(palette.id, colorIndex, normalized)
                    render()
                }

                override fun mousePressed(event: java.awt.event.MouseEvent) {
                    if (event.isPopupTrigger) showColorMenu(this@apply, palette, colorIndex, event.x, event.y)
                }

                override fun mouseReleased(event: java.awt.event.MouseEvent) {
                    if (event.isPopupTrigger) showColorMenu(this@apply, palette, colorIndex, event.x, event.y)
                }
            })
        }

    private fun showColorMenu(parent: JComponent, palette: RainbowPalette, colorIndex: Int, x: Int, y: Int) {
        JPopupMenu().apply {
            add(JMenuItem("Remove color").apply {
                isEnabled = palette.lightColors.size > RainbowPalette.MIN_COLOR_COUNT
                addActionListener {
                    editorModel.removeColor(palette.id, colorIndex)
                    render()
                }
            })
        }.show(parent, x, y)
    }


}
