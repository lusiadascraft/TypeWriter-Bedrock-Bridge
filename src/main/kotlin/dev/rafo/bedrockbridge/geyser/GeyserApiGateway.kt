package dev.rafo.bedrockbridge.geyser

import dev.rafo.bedrockbridge.state.CinematicSession
import dev.rafo.bedrockbridge.sound.BedrockSoundRequest
import dev.rafo.bedrockbridge.sound.SoundPosition
import org.cloudburstmc.math.vector.Vector3f
import org.geysermc.geyser.api.GeyserApi
import org.geysermc.geyser.api.bedrock.camera.CameraData
import org.geysermc.geyser.api.bedrock.camera.GuiElement
import java.nio.file.Path
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * The only statically linked Geyser class in the extension. This adapter is loaded reflectively
 * after Geyser-Spigot is known to be active, so a server without Geyser can still load the extension.
 */
internal class GeyserApiGateway : BedrockGateway {
    private val api = requireNotNull(GeyserApi.api()) {
        "Geyser-Spigot está ativo, mas GeyserApi.api() ainda não está disponível"
    }
    private val soundFailure = AtomicReference<String?>(null)
    private val soundSender = runCatching {
        ReflectiveGeyserSoundSender(api.javaClass.classLoader)
    }.onFailure {
        soundFailure.set(it.message ?: it.javaClass.simpleName)
    }.getOrNull()

    override val available: Boolean = true
    override val status: String = "API pública do Geyser disponível"
    override val apiVersion: String = api.geyserApiVersion().toString()
    override val soundTransportAvailable: Boolean
        get() = soundSender != null && soundFailure.get() == null
    override val soundStatus: String
        get() = soundFailure.get()?.let { "incompatível: $it" } ?: "PlaySoundPacket disponível"

    override fun isBedrockPlayer(playerId: UUID): Boolean = api.connectionByUuid(playerId) != null

    override fun openHudSession(playerId: UUID): CinematicSession? {
        val connection = api.connectionByUuid(playerId) ?: return null
        return RestoringHudSession(GeyserHudCamera(connection.camera()))
    }

    override fun packDirectory(): Path = api.packDirectory()

    override fun playSound(playerId: UUID, request: BedrockSoundRequest): Boolean {
        val sender = soundSender ?: return false
        if (soundFailure.get() != null) return false
        val connection = api.connectionByUuid(playerId) ?: return false

        return runCatching { sender.send(connection, request) }
            .onFailure { soundFailure.compareAndSet(null, it.message ?: it.javaClass.simpleName) }
            .getOrDefault(false)
    }
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

/**
 * Compatibility boundary for the only undocumented Geyser call used by BedrockBridge.
 *
 * Verified against the exact Typewriter 0.8.0 dependency snapshots:
 * - Geyser API 2.4.2-20240914.223501-32
 * - Geyser core 2.4.2-20240914.223501-31 (SHA-256
 *   5BC009C743A649C676C3AE17684301949ED91C72A67B6644A90FA627962892C8)
 * - Cloudburst bedrock-codec 3.0.0.Beta4-20240828.162251-1
 *
 * That core routes custom sounds through PlaySoundPacket and exposes the concrete public method
 * GeyserSession#sendUpstreamPacket(BedrockPacket), neither of which belongs to the public Geyser
 * API. Reflection prevents a hard link to those internals. A lookup/invocation failure returns to
 * the caller without cancelling the original Java packet, allowing Geyser's normal translator to
 * handle it instead.
 */
private class ReflectiveGeyserSoundSender(classLoader: ClassLoader) {
    private val packetClass = Class.forName(PLAY_SOUND_PACKET, true, classLoader)
    private val packetConstructor = packetClass.getDeclaredConstructor()
    private val setSound = packetClass.getMethod("setSound", String::class.java)
    private val setPosition = packetClass.getMethod("setPosition", Vector3f::class.java)
    private val setVolume = packetClass.getMethod("setVolume", Float::class.javaPrimitiveType)
    private val setPitch = packetClass.getMethod("setPitch", Float::class.javaPrimitiveType)
    private val sendMethods = ConcurrentHashMap<Class<*>, Method>()

    fun send(connection: Any, request: BedrockSoundRequest): Boolean {
        val packet = packetConstructor.newInstance()
        val position = request.position.resolve(connection) ?: return false

        setSound.invoke(packet, request.identifier)
        setPosition.invoke(packet, position)
        setVolume.invoke(packet, request.volume)
        setPitch.invoke(packet, request.pitch)
        sendMethod(connection.javaClass).invoke(connection, packet)
        return true
    }

    private fun sendMethod(connectionClass: Class<*>): Method = sendMethods.computeIfAbsent(connectionClass) { type ->
        type.methods.firstOrNull { method ->
            method.name == "sendUpstreamPacket" &&
                method.parameterCount == 1 &&
                method.parameterTypes.single().isAssignableFrom(packetClass)
        } ?: error("GeyserSession#sendUpstreamPacket(BedrockPacket) não foi encontrado")
    }

    private fun SoundPosition.resolve(connection: Any): Vector3f? = when (this) {
        is SoundPosition.Absolute -> vector()
        is SoundPosition.Entity -> entityPosition(connection, javaEntityId)
    }

    private fun SoundPosition.Absolute.vector(): Vector3f = Vector3f.from(x, y, z)

    private fun entityPosition(connection: Any, javaEntityId: Int): Vector3f? = runCatching {
        val entityCache = connection.javaClass.getMethod("getEntityCache").invoke(connection)
        val entity = entityCache.javaClass
            .getMethod("getEntityByJavaId", Int::class.javaPrimitiveType)
            .invoke(entityCache, javaEntityId)
            ?: return null
        entity.javaClass.getMethod("getPosition").invoke(entity) as? Vector3f
    }.getOrNull()

    private companion object {
        const val PLAY_SOUND_PACKET = "org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket"
    }
}
