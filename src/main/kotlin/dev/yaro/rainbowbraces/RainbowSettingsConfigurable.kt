// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.yaro.rainbowbraces.palette.PaletteEditorModel
import dev.yaro.rainbowbraces.palette.PaletteEditorPanel
import dev.yaro.rainbowbraces.settings.RainbowDefaultFileTypes
import dev.yaro.rainbowbraces.settings.RainbowPreferences
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import dev.yaro.rainbowbraces.settings.normalizeExtensions
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * Coordinates a transactional settings draft: widgets hold general options and
 * PaletteEditorModel holds unsaved palette edits. Only Apply writes to the service.
 * Reset reloads the last applied values; disposing the UI discards the draft.
 * Live preview affects palette swatches locally, not open editors before Apply.
 */
class RainbowSettingsConfigurable : SearchableConfigurable {
    private var preferences = RainbowPreferences()
    private val paletteEditor = PaletteEditorModel(preferences)
    private var palettesPanel: PaletteEditorPanel? = null
    private var enabledCheckBox: JCheckBox? = null
    private var pairEmphasisCheckBox: JCheckBox? = null
    private var indentGuidesCheckBox: JCheckBox? = null
    private var activeGuideTransparencySlider: JSlider? = null
    private var inactiveGuideTransparencySlider: JSlider? = null
    private var saturationSlider: JSlider? = null
    private var brightnessSlider: JSlider? = null
    private var extensionsField: JTextField? = null

    override fun getId(): String = "dev.yaro.rainbowbraces.settings"
    override fun getDisplayName(): String = "Rainbow Delimiters"

    override fun createComponent(): JComponent {
        loadFromService()
        return JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(8)
            add(createToolbar(), BorderLayout.NORTH)
            palettesPanel = PaletteEditorPanel(paletteEditor)
            add(palettesPanel, BorderLayout.CENTER)
            updatePreview()
        }
    }

    override fun isModified(): Boolean = readPreferences() != RainbowSettingsService.getInstance().preferences()

    override fun apply() {
        RainbowSettingsService.getInstance().updatePreferences(readPreferences())
        reset()
        // Settings are application-wide, including editors in other open projects.
        EditorFactory.getInstance().allEditors.forEach(RainbowBracesEditorListener::refreshEditor)
    }

    override fun reset() {
        loadFromService()
        syncGeneralControls()
        palettesPanel?.render()
        updatePreview()
    }

    override fun disposeUIResources() {
        palettesPanel = null
        enabledCheckBox = null
        pairEmphasisCheckBox = null
        indentGuidesCheckBox = null
        activeGuideTransparencySlider = null
        inactiveGuideTransparencySlider = null
        saturationSlider = null
        brightnessSlider = null
        extensionsField = null
        preferences = RainbowPreferences()
        paletteEditor.reset(preferences)
    }

    private fun loadFromService() {
        preferences = RainbowSettingsService.getInstance().preferences()
        paletteEditor.reset(preferences)
    }

    // Build a detached candidate for both isModified and Apply. Null widget fallbacks
    // also allow the platform to query this configurable before creating its UI.
    private fun readPreferences(): RainbowPreferences = preferences.copy(
        enabled = enabledCheckBox?.isSelected ?: preferences.enabled,
        pairEmphasisEnabled = pairEmphasisCheckBox?.isSelected ?: preferences.pairEmphasisEnabled,
        indentGuidesEnabled = indentGuidesCheckBox?.isSelected ?: preferences.indentGuidesEnabled,
        activeIndentGuideTransparency = activeGuideTransparencySlider?.value ?: preferences.activeIndentGuideTransparency,
        inactiveIndentGuideTransparency = inactiveGuideTransparencySlider?.value ?: preferences.inactiveIndentGuideTransparency,
        colorSaturation = saturationSlider?.value ?: preferences.colorSaturation,
        colorBrightness = brightnessSlider?.value ?: preferences.colorBrightness,
        activePaletteId = paletteEditor.activePaletteId,
        palettes = paletteEditor.palettes,
        enabledExtensions = extensionsField?.text?.let { normalizeExtensions(it.split(',', ';', ' ', '\n', '\t')) }
            ?: preferences.enabledExtensions
    )

    private fun syncGeneralControls() {
        enabledCheckBox?.isSelected = preferences.enabled
        pairEmphasisCheckBox?.isSelected = preferences.pairEmphasisEnabled
        indentGuidesCheckBox?.isSelected = preferences.indentGuidesEnabled
        activeGuideTransparencySlider?.value = preferences.activeIndentGuideTransparency
        inactiveGuideTransparencySlider?.value = preferences.inactiveIndentGuideTransparency
        saturationSlider?.value = preferences.colorSaturation
        brightnessSlider?.value = preferences.colorBrightness
        extensionsField?.text = preferences.enabledExtensions.joinToString(", ")
    }

    private fun createToolbar(): JPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                enabledCheckBox = JCheckBox("Enabled", preferences.enabled)
                add(enabledCheckBox)
                pairEmphasisCheckBox = JCheckBox("Highlight matching pair", preferences.pairEmphasisEnabled)
                add(pairEmphasisCheckBox)
                add(JButton("+ Palette").apply {
                    addActionListener {
                        palettesPanel?.duplicateActivePalette()
                    }
                })
                add(JButton("Import").apply {
                    addActionListener { palettesPanel?.importPalettes() }
                })
                add(JButton("Export").apply {
                    addActionListener { palettesPanel?.exportPalettes() }
                })
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
                indentGuidesCheckBox = JCheckBox("Color indent guides", preferences.indentGuidesEnabled).apply {
                    toolTipText = "Color multiline {}, () and [] guides and add gray case/default guides in C#, C and C++"
                }
                add(indentGuidesCheckBox)
            })
            add(createTransparencyRow("Active guide transparency:", preferences.activeIndentGuideTransparency, "activeIndentGuideTransparency") {
                activeGuideTransparencySlider = it
            })
            add(createTransparencyRow("Inactive guide transparency:", preferences.inactiveIndentGuideTransparency, "inactiveIndentGuideTransparency") {
                inactiveGuideTransparencySlider = it
            })
            add(createPercentageRow("Color saturation:", preferences.colorSaturation, "colorSaturation",
                "100% = original palette saturation, 0% = grayscale. Preview updates immediately.", ::updatePreview) {
                saturationSlider = it
            })
            add(createPercentageRow("Color brightness:", preferences.colorBrightness, "colorBrightness",
                "100% = original palette brightness, 0% = black. Preview updates immediately.", ::updatePreview) {
                brightnessSlider = it
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
                add(JBLabel("Extensions:"))
                extensionsField = JTextField(preferences.enabledExtensions.joinToString(", ")).apply {
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

    private fun createTransparencyRow(label: String, value: Int, controlName: String, bind: (JSlider) -> Unit): JPanel =
        createPercentageRow(label, value, controlName,
            "0% = fully visible, 100% = invisible. Active means a brace is selected or next to a caret.", {}, bind)

    private fun createPercentageRow(
        label: String, value: Int, controlName: String, tooltip: String, onChange: () -> Unit, bind: (JSlider) -> Unit
    ): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            val percentage = JBLabel("$value%").apply {
                preferredSize = Dimension(JBUI.scale(40), preferredSize.height)
            }
            val slider = JSlider(0, 100, value).apply {
                name = controlName
                preferredSize = Dimension(JBUI.scale(200), preferredSize.height)
                toolTipText = tooltip
                accessibleContext.accessibleName = label
                addChangeListener {
                    percentage.text = "${this.value}%"
                    onChange()
                }
            }
            add(JBLabel(label).apply {
                preferredSize = Dimension(JBUI.scale(185), preferredSize.height)
                labelFor = slider
            })
            add(slider)
            add(percentage)
            bind(slider)
        }

    private fun updatePreview() {
        // Update existing swatches rather than rebuilding controls while dragging.
        // Raw palette colors remain available unchanged to the color picker/export.
        palettesPanel?.setColorAdjustment(
            saturationSlider?.value ?: preferences.colorSaturation,
            brightnessSlider?.value ?: preferences.colorBrightness)
    }
}
