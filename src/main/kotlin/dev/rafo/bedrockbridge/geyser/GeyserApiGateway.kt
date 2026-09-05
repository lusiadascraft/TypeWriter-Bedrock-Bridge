package dev.rafo.bedrockbridge.geyser

import dev.rafo.bedrockbridge.state.CinematicSession
import org.geysermc.geyser.api.GeyserApi
import org.geysermc.geyser.api.bedrock.camera.CameraData
import org.geysermc.geyser.api.bedrock.camera.GuiElement
import java.nio.file.Path
import java.util.UUID

/**
 * The only statically linked Geyser class in the extension. This adapter is loaded reflectively
 * after Geyser-Spigot is known to be active, so a server without Geyser can still load the extension.
 */
internal class GeyserApiGateway : BedrockGateway {
    private val api = requireNotNull(GeyserApi.api()) {
        "Geyser-Spigot está ativo, mas GeyserApi.api() ainda não está disponível"
    }

    override val available: Boolean = true
    override val status: String = "API pública do Geyser disponível"
    override val apiVersion: String = api.geyserApiVersion().toString()

    override fun isBedrockPlayer(playerId: UUID): Boolean = api.connectionByUuid(playerId) != null

    override fun openHudSession(playerId: UUID): CinematicSession? {
        val connection = api.connectionByUuid(playerId) ?: return null
        return RestoringHudSession(GeyserHudCamera(connection.camera()))
    }

    override fun packDirectory(): Path = api.packDirectory()
}

private class GeyserHudCamera(
    private val camera: CameraData,
) : HudCamera {
    override fun hiddenElements(): Set<HudElement> = camera.hiddenElements()
        .mapNotNull(GEYSER_TO_HUD::get)
        .toSet()

    override fun hide(elements: Set<HudElement>) {
        camera.hideElement(*elements.map(HUD_TO_GEYSER::getValue).toTypedArray())
    }

    override fun reset(elements: Set<HudElement>) {
        camera.resetElement(*elements.map(HUD_TO_GEYSER::getValue).toTypedArray())
    }
}

private val HUD_TO_GEYSER = mapOf(
    HudElement.PAPER_DOLL to GuiElement.PAPER_DOLL,
    HudElement.ARMOR to GuiElement.ARMOR,
    HudElement.TOOL_TIPS to GuiElement.TOOL_TIPS,
    HudElement.TOUCH_CONTROLS to GuiElement.TOUCH_CONTROLS,
    HudElement.CROSSHAIR to GuiElement.CROSSHAIR,
    HudElement.HOTBAR to GuiElement.HOTBAR,
    HudElement.HEALTH to GuiElement.HEALTH,
    HudElement.PROGRESS_BAR to GuiElement.PROGRESS_BAR,
    HudElement.FOOD_BAR to GuiElement.FOOD_BAR,
    HudElement.AIR_BUBBLES_BAR to GuiElement.AIR_BUBBLES_BAR,
    HudElement.VEHICLE_HEALTH to GuiElement.VEHICLE_HEALTH,
    HudElement.EFFECTS_BAR to GuiElement.EFFECTS_BAR,
    HudElement.ITEM_TEXT_POPUP to GuiElement.ITEM_TEXT_POPUP,
)

private val GEYSER_TO_HUD = HUD_TO_GEYSER.entries.associate { (hud, geyser) -> geyser to hud }
