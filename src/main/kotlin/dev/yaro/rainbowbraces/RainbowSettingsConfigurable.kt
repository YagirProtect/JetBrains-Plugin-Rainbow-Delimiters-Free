package dev.yaro.rainbowbraces

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JSeparator
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButton
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter

class RainbowSettingsConfigurable : SearchableConfigurable {
    private var panel: JPanel? = null
    private var palettesPanel: JPanel? = null
    private var enabledCheckBox: JCheckBox? = null
    private var pairEmphasisCheckBox: JCheckBox? = null
    private var extensionsField: JTextField? = null

    private var enabled: Boolean = true
    private var pairEmphasisEnabled: Boolean = true
    private var activePaletteId: String = RainbowBuiltInPalettes.default.id
    private var workingPalettes: MutableList<RainbowPaletteState> = arrayListOf()
    private var enabledExtensions: MutableList<String> = ArrayList(RainbowDefaultFileTypes.extensions)

    override fun getId(): String = "dev.yaro.rainbowbraces.settings"

    override fun getDisplayName(): String = "Rainbow Delimiters"

    override fun createComponent(): JComponent {
        loadFromService()

        val root = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(8)
        }

        root.add(createToolbar(), BorderLayout.NORTH)

        palettesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val scrollContent = JPanel(BorderLayout()).apply {
            add(palettesPanel, BorderLayout.NORTH)
        }
        root.add(JBScrollPane(scrollContent), BorderLayout.CENTER)

        panel = root
        renderPalettes()
        return root
    }

    override fun isModified(): Boolean {
        val service = RainbowSettingsService.getInstance()
        val uiEnabled = enabledCheckBox?.isSelected ?: enabled
        val uiPairEmphasisEnabled = pairEmphasisCheckBox?.isSelected ?: pairEmphasisEnabled
        val uiExtensions = parseExtensions(extensionsField?.text.orEmpty())
        return service.state.enabled != uiEnabled ||
            service.state.pairEmphasisEnabled != uiPairEmphasisEnabled ||
            service.state.activePaletteId != activePaletteId ||
            service.state.enabledExtensions != uiExtensions ||
            snapshot(service.palettes()) != snapshot(workingPalettes)
    }

    override fun apply() {
        val uiEnabled = enabledCheckBox?.isSelected ?: enabled
        val uiPairEmphasisEnabled = pairEmphasisCheckBox?.isSelected ?: pairEmphasisEnabled
        val uiExtensions = parseExtensions(extensionsField?.text.orEmpty())
        RainbowSettingsService.getInstance().update(
            uiEnabled,
            uiPairEmphasisEnabled,
            activePaletteId,
            workingPalettes,
            uiExtensions
        )
        loadFromService()
        syncGeneralControls()
        renderPalettes()
        EditorFactory.getInstance().allEditors.forEach(RainbowBracesEditorListener::refreshEditor)
    }

    override fun reset() {
        loadFromService()
        syncGeneralControls()
        renderPalettes()
    }

    override fun disposeUIResources() {
        panel = null
        palettesPanel = null
        enabledCheckBox = null
        pairEmphasisCheckBox = null
        extensionsField = null
        workingPalettes.clear()
    }

    private fun loadFromService() {
        val service = RainbowSettingsService.getInstance()
        enabled = service.state.enabled
        pairEmphasisEnabled = service.state.pairEmphasisEnabled
        activePaletteId = service.state.activePaletteId
        workingPalettes = service.palettes().mapTo(arrayListOf()) { it.copyPalette() }
        enabledExtensions = ArrayList(service.state.enabledExtensions)
    }

    private fun createToolbar(): JPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                enabledCheckBox = JCheckBox("Enabled", enabled)
                add(enabledCheckBox)
                pairEmphasisCheckBox = JCheckBox("Highlight matching pair", pairEmphasisEnabled)
                add(pairEmphasisCheckBox)
                add(JButton("+ Palette").apply {
                    addActionListener {
                        val copy = activePalette().copyAsCustom()
                        workingPalettes.add(copy)
                        activePaletteId = copy.id
                        renderPalettes()
                    }
                })
                add(JButton("Import").apply {
                    addActionListener { importPalettes() }
                })
                add(JButton("Export").apply {
                    addActionListener { exportPalettes() }
                })
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
                add(JBLabel("Extensions:"))
                extensionsField = JTextField(enabledExtensions.joinToString(", ")).apply {
                    preferredSize = Dimension(JBUI.scale(420), preferredSize.height)
                    toolTipText = "Comma-separated file extensions without dots"
                }
                add(extensionsField)
                add(JButton("Defaults").apply {
                    addActionListener {
                        extensionsField?.text = RainbowDefaultFileTypes.extensions.joinToString(", ")
                    }
                })
            })
        }

    private fun syncGeneralControls() {
        enabledCheckBox?.isSelected = enabled
        pairEmphasisCheckBox?.isSelected = pairEmphasisEnabled
        extensionsField?.text = enabledExtensions.joinToString(", ")
    }

    private fun renderPalettes() {
        val target = palettesPanel ?: return
        target.removeAll()

        workingPalettes.forEachIndexed { index, palette ->
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

    private fun createPaletteRow(palette: RainbowPaletteState): JPanel {
        val row = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            border = JBUI.Borders.emptyBottom(4)
        }

        val header = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)))
        val colorsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        header.add(JRadioButton().apply {
            isSelected = palette.id == activePaletteId
            addActionListener {
                activePaletteId = palette.id
                renderPalettes()
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
                    palette.name = text.ifBlank { "Custom palette" }
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
                    resetBuiltInPalette(palette)
                    renderPalettes()
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
                    deletePalette(palette)
                    renderPalettes()
                }
            })
        }

        row.add(header, BorderLayout.NORTH)
        row.add(colorsPanel, BorderLayout.CENTER)
        return row
    }

    private fun createAddColorSwatch(palette: RainbowPaletteState): JComponent =
        AddColorSwatch().apply {
            preferredSize = Dimension(JBUI.scale(24), JBUI.scale(24))
            minimumSize = preferredSize
            maximumSize = preferredSize
            toolTipText = "Add color"
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(event: java.awt.event.MouseEvent) {
                    if (!javax.swing.SwingUtilities.isLeftMouseButton(event)) return
                    event.consume()
                    addColor(palette)
                    javax.swing.SwingUtilities.invokeLater { renderPalettes() }
                }
            })
        }

    private fun createColorSwatch(palette: RainbowPaletteState, colorIndex: Int, rgb: Int): JComponent =
        ColorSwatch(Color(rgb)).apply {
            preferredSize = Dimension(JBUI.scale(24), JBUI.scale(24))
            minimumSize = preferredSize
            maximumSize = preferredSize
            toolTipText = "Choose color"
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(event: java.awt.event.MouseEvent) {
                    if (event.isPopupTrigger || javax.swing.SwingUtilities.isRightMouseButton(event)) {
                        showColorMenu(this@apply, palette, colorIndex, event.x, event.y)
                        return
                    }
                    val chosen = showColorDialog(this@apply, Color(palette.lightColors[colorIndex]))
                        ?: return
                    val normalized = chosen.rgb and 0xFFFFFF
                    palette.lightColors[colorIndex] = normalized
                    palette.darkColors[colorIndex] = normalized
                    renderPalettes()
                }

                override fun mousePressed(event: java.awt.event.MouseEvent) {
                    if (event.isPopupTrigger) showColorMenu(this@apply, palette, colorIndex, event.x, event.y)
                }

                override fun mouseReleased(event: java.awt.event.MouseEvent) {
                    if (event.isPopupTrigger) showColorMenu(this@apply, palette, colorIndex, event.x, event.y)
                }
            })
        }

    private fun showColorMenu(parent: JComponent, palette: RainbowPaletteState, colorIndex: Int, x: Int, y: Int) {
        JPopupMenu().apply {
            add(JMenuItem("Remove color").apply {
                isEnabled = palette.lightColors.size > RainbowSettingsState.MIN_COLOR_COUNT
                addActionListener {
                    removeColor(palette, colorIndex)
                    renderPalettes()
                }
            })
        }.show(parent, x, y)
    }

    private fun addColor(palette: RainbowPaletteState) {
        val color = palette.lightColors.lastOrNull() ?: 0xFFFFFF
        palette.lightColors.add(color)
        palette.darkColors.add(color)
    }

    private fun removeColor(palette: RainbowPaletteState, colorIndex: Int) {
        if (palette.lightColors.size <= RainbowSettingsState.MIN_COLOR_COUNT) return
        if (colorIndex !in palette.lightColors.indices) return

        palette.lightColors.removeAt(colorIndex)
        palette.darkColors.removeAt(colorIndex)
    }

    private fun showColorDialog(parent: JComponent, initial: Color): Color? {
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

    private class ColorSwatch(var color: Color) : JBPanel<ColorSwatch>() {
        init {
            border = BorderFactory.createLineBorder(JBColorBorderColor.get())
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

    private class AddColorSwatch : JBPanel<AddColorSwatch>() {
        init {
            border = BorderFactory.createLineBorder(JBColorBorderColor.get())
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

    private fun resetBuiltInPalette(palette: RainbowPaletteState) {
        val builtIn = RainbowBuiltInPalettes.find(palette.builtInId) ?: return
        palette.lightColors = ArrayList(builtIn.lightColors)
        palette.darkColors = ArrayList(builtIn.darkColors)
    }

    private fun deletePalette(palette: RainbowPaletteState) {
        if (palette.isBuiltIn()) return

        val index = workingPalettes.indexOfFirst { it.id == palette.id }
        workingPalettes.removeAll { it.id == palette.id }
        if (activePaletteId == palette.id) {
            activePaletteId = workingPalettes.getOrNull(index.coerceAtMost(workingPalettes.lastIndex))?.id
                ?: RainbowBuiltInPalettes.default.id
        }
    }

    private fun activePalette(): RainbowPaletteState =
        workingPalettes.firstOrNull { it.id == activePaletteId } ?: workingPalettes.first()

    private fun exportPalettes() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export Palettes"
            fileFilter = FileNameExtensionFilter("JSON files", "json")
            selectedFile = File("rainbow-delimiters-palettes.json")
        }
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return

        val file = chooser.selectedFile.ensureJsonExtension()
        file.writeText(encodePalettes(), Charsets.UTF_8)
    }

    private fun importPalettes() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Import Palettes"
            fileFilter = FileNameExtensionFilter("JSON files", "json")
        }
        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return

        try {
            val imported = decodePalettes(chooser.selectedFile.readText(Charsets.UTF_8))
            if (imported.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "No palettes found in selected file.", "Import Palettes", JOptionPane.WARNING_MESSAGE)
                return
            }

            val builtInIds = RainbowBuiltInPalettes.all.map { it.id }.toSet()
            val builtIns = RainbowBuiltInPalettes.all.mapTo(arrayListOf()) { builtIn ->
                imported.firstOrNull { it.id == builtIn.id } ?: workingPalettes.firstOrNull { it.id == builtIn.id } ?: builtIn.toState()
            }
            val custom = imported.filterNot { it.id in builtInIds }
            workingPalettes = ArrayList(builtIns + custom)
            if (workingPalettes.none { it.id == activePaletteId }) {
                activePaletteId = workingPalettes.first().id
            }
            renderPalettes()
        } catch (t: Throwable) {
            JOptionPane.showMessageDialog(panel, "Could not import palettes: ${t.message}", "Import Palettes", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun encodePalettes(): String {
        val palettesJson = workingPalettes.joinToString(",\n") { palette ->
            val colors = palette.lightColors.joinToString(", ") { "\"#${it.toRgbHex()}\"" }
            """
              {
                "id": "${palette.id.escapeJson()}",
                "name": "${palette.name.escapeJson()}",
                "builtInId": "${palette.builtInId.escapeJson()}",
                "colors": [$colors]
              }""".trimIndent()
        }
        return """
            {
              "version": 1,
              "activePaletteId": "${activePaletteId.escapeJson()}",
              "palettes": [
            $palettesJson
              ]
            }
        """.trimIndent()
    }

    private fun decodePalettes(text: String): List<RainbowPaletteState> {
        val paletteRegex = Regex("""\{\s*"id"\s*:\s*"((?:\\.|[^"])*)"\s*,\s*"name"\s*:\s*"((?:\\.|[^"])*)"\s*,\s*"builtInId"\s*:\s*"((?:\\.|[^"])*)"\s*,\s*"colors"\s*:\s*\[(.*?)\]\s*}""", RegexOption.DOT_MATCHES_ALL)
        val colorRegex = Regex(""""#?([0-9a-fA-F]{6})"""")

        return paletteRegex.findAll(text).mapNotNull { match ->
            val colors = colorRegex.findAll(match.groupValues[4])
                .map { it.groupValues[1].toInt(16) }
                .toMutableList()
            if (colors.size < RainbowSettingsState.MIN_COLOR_COUNT) return@mapNotNull null

            RainbowPaletteState().also {
                it.id = match.groupValues[1].unescapeJson().ifBlank { "custom-${java.util.UUID.randomUUID()}" }
                it.name = match.groupValues[2].unescapeJson().ifBlank { "Imported palette" }
                it.builtInId = match.groupValues[3].unescapeJson()
                it.lightColors = ArrayList(colors)
                it.darkColors = ArrayList(colors)
            }
        }.toList()
    }

    private fun snapshot(palettes: List<RainbowPaletteState>): String =
        palettes.joinToString("|") { palette ->
            listOf(
                palette.id,
                palette.name,
                palette.builtInId,
                palette.lightColors.joinToString(","),
                palette.darkColors.joinToString(",")
            ).joinToString(":")
        }
}

private object JBColorBorderColor {
    fun get(): Color = Color(0x6E6E6E)
}

private class SimpleDocumentListener(private val onChange: () -> Unit) : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) = onChange()
    override fun removeUpdate(e: DocumentEvent) = onChange()
    override fun changedUpdate(e: DocumentEvent) = onChange()
}

private fun File.ensureJsonExtension(): File =
    if (extension.equals("json", ignoreCase = true)) this else File(parentFile, "$name.json")

private fun Int.toRgbHex(): String = and(0xFFFFFF).toString(16).uppercase().padStart(6, '0')

private fun String.escapeJson(): String =
    buildString {
        this@escapeJson.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

private fun String.unescapeJson(): String =
    replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

private fun parseExtensions(text: String): MutableList<String> =
    text.split(',', ';', ' ', '\n', '\t')
        .map { it.trim().removePrefix(".").lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .toMutableList()
