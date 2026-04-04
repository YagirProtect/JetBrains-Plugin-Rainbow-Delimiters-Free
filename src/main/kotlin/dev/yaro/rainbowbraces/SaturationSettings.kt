package dev.yaro.rainbowbraces

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(
    name = "RainbowBracesSaturation",
    storages = [Storage("RainbowBracesSaturation.xml")]
)
class SaturationSettings : PersistentStateComponent<SaturationSettings> {
    var saturation: Float = 1.0f
    var brightness: Float = 1.0f

    override fun getState(): SaturationSettings = this
    override fun loadState(state: SaturationSettings) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): SaturationSettings = service()
    }
}