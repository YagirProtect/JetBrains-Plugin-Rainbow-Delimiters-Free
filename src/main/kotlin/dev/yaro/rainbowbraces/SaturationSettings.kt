package dev.yaro.rainbowbraces

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "PaletteSaturationSettings",
    storages = [Storage("PaletteSaturationSettings.xml")]
)
class SaturationSettings : PersistentStateComponent<SaturationSettings> {
    var saturation: Float = 1.0f

    companion object {
        fun getInstance(): SaturationSettings {
            return com.intellij.openapi.components.ServiceManager.getService(SaturationSettings::class.java)
        }
    }

    override fun getState(): SaturationSettings = this
    override fun loadState(state: SaturationSettings) = XmlSerializerUtil.copyBean(state, this)
}