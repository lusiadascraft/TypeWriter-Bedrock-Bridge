package dev.rafo.bedrockbridge.geyser

import dev.rafo.bedrockbridge.protocol.BridgeMessage
import dev.rafo.bedrockbridge.protocol.SoundPosition.Absolute
import dev.rafo.bedrockbridge.protocol.SoundPosition.Entity
import dev.rafo.bedrockbridge.sound.BedrockSoundCatalog
import dev.rafo.bedrockbridge.sound.BedrockSoundRequest
import dev.rafo.bedrockbridge.state.CinematicSession
import dev.rafo.bedrockbridge.transport.VelocityBridgeTransport
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

internal class FloodgateVelocityGateway(
    private val floodgate: ReflectiveFloodgateDetector,
    private val transport: VelocityBridgeTransport,
) : BedrockGateway {
    override val available: Boolean = true
    override val status: String
        get() = if (transport.readyCount == 0) {
            "Floodgate disponível; à espera do BedrockBridge no Velocity"
        } else {
            "ponte Velocity ligada a ${transport.readyCount} jogador(es)"
        }
    override val apiVersion: String = floodgate.version
    override val soundTransportAvailable: Boolean = true
    override val soundStatus: String
        get() = when {
            transport.readyCount == 0 -> "à espera do proxy Velocity"
            transport.soundReadyCount == 0 -> "proxy ligado; áudio direto indisponível"
            !transport.catalogReady -> "proxy ligado; catálogo de sons a sincronizar"
            else -> "proxy ligado; ${transport.soundDefinitions} som(ns) sincronizado(s)"
        }

    override fun isBedrockPlayer(playerId: UUID): Boolean = floodgate.isBedrockPlayer(playerId)

    override fun openHudSession(playerId: UUID): CinematicSession? {
        if (!isBedrockPlayer(playerId)) return null
        return RelayedHudSession(playerId, ThreadLocalRandom.current().nextLong(), transport)
    }

    override fun packDirectory() = null

    override fun resolveSound(javaIdentifier: String, localCatalog: BedrockSoundCatalog): String? =
        transport.resolveSound(javaIdentifier)

    override fun soundDefinitionCount(localCatalog: BedrockSoundCatalog): Int = transport.soundDefinitions

    override fun playSound(playerId: UUID, request: BedrockSoundRequest): Boolean {
        val position = when (val source = request.position) {
            is dev.rafo.bedrockbridge.sound.SoundPosition.Absolute -> Absolute(source.x, source.y, source.z)
            is dev.rafo.bedrockbridge.sound.SoundPosition.Entity -> Entity(source.javaEntityId)
        }
        return transport.sendSound(
            playerId,
            BridgeMessage.PlaySound(request.identifier, position, request.volume, request.pitch),
        )
    }

    override fun close() = transport.close()
}

private class RelayedHudSession(
    private val playerId: UUID,
    private val sessionId: Long,
    private val transport: VelocityBridgeTransport,
) : CinematicSession {
    private val closed = AtomicBoolean(false)

    init {
        keepActive()
    }

    override fun keepActive() {
        if (!closed.get()) transport.send(playerId, BridgeMessage.HudHide(sessionId))
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            transport.send(playerId, BridgeMessage.HudReset(sessionId))
        }
    }
}

internal class ReflectiveFloodgateDetector(
    classLoader: ClassLoader,
    val version: String,
) {
    private val apiClass = Class.forName(FLOODGATE_API, true, classLoader)
    private val api = requireNotNull(apiClass.getMethod("getInstance").invoke(null)) {
        "FloodgateApi.getInstance() ainda não está disponível"
    }
    private val isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID::class.java)

    fun isBedrockPlayer(playerId: UUID): Boolean = isFloodgatePlayer.invoke(api, playerId) == true

    private companion object {
        const val FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi"
    }
}

internal object FloodgateVelocityGatewayLoader {
    fun load(
        floodgatePlugin: Plugin?,
        typewriterPlugin: Plugin,
        onFailure: (Throwable) -> Unit = {},
    ): BedrockGateway {
        if (floodgatePlugin == null) {
            return UnavailableBedrockGateway("Geyser-Spigot ou Floodgate não estão instalados ou ativos")
        }

        return runCatching {
            val detector = ReflectiveFloodgateDetector(
                floodgatePlugin.javaClass.classLoader,
                floodgatePlugin.pluginMeta.version,
            )
            val transport = VelocityBridgeTransport(typewriterPlugin) { operation, error ->
                onFailure(IllegalStateException("Falha ao $operation: ${error.message}", error))
            }.also(VelocityBridgeTransport::start)
            FloodgateVelocityGateway(detector, transport)
        }.getOrElse { error ->
            onFailure(error)
            UnavailableBedrockGateway(
                "a API do Floodgate não está disponível: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }
}
