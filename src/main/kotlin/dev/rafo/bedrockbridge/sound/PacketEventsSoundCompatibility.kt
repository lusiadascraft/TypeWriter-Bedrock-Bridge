package dev.rafo.bedrockbridge.sound

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect
import dev.rafo.bedrockbridge.geyser.BedrockGateway
import dev.rafo.bedrockbridge.state.CinematicPlayerRegistry
import java.util.concurrent.atomic.AtomicBoolean

internal class PacketEventsSoundCompatibility(
    gateway: BedrockGateway,
    players: CinematicPlayerRegistry,
    val catalog: BedrockSoundCatalog,
) : AutoCloseable {
    private val bridge = CinematicSoundBridge(
        isCinematicActive = players::isActive,
        resolveSound = { gateway.resolveSound(it, catalog) },
        sendSound = gateway::playSound,
    )
    private val listener = SoundPacketListener(bridge)
    private val eventManager = PacketEvents.getAPI().eventManager
    private val closed = AtomicBoolean(false)

    val forwardedSounds: Long
        get() = bridge.forwardedSounds

    init {
        check(PacketEvents.getAPI().isInitialized) { "PacketEvents ainda não está inicializado" }
        eventManager.registerListener(listener)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        eventManager.unregisterListener(listener)
    }
}

private class SoundPacketListener(
    private val bridge: CinematicSoundBridge,
) : PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
    override fun onPacketSend(event: PacketSendEvent) {
        if (event.isCancelled) return
        val playerId = event.user.uuid ?: return

        val forwarded = when (event.packetType) {
            PacketType.Play.Server.SOUND_EFFECT -> {
                val packet = WrapperPlayServerSoundEffect(event)
                val position = packet.effectPosition
                bridge.forward(
                    playerId = playerId,
                    javaIdentifier = packet.sound.soundId.toString(),
                    position = SoundPosition.Absolute(
                        x = position.x / POSITION_SCALE,
                        y = position.y / POSITION_SCALE,
                        z = position.z / POSITION_SCALE,
                    ),
                    volume = packet.volume,
                    pitch = packet.pitch,
                )
            }

            PacketType.Play.Server.ENTITY_SOUND_EFFECT -> {
                val packet = WrapperPlayServerEntitySoundEffect(event)
                bridge.forward(
                    playerId = playerId,
                    javaIdentifier = packet.sound.soundId.toString(),
                    position = SoundPosition.Entity(packet.entityId),
                    volume = packet.volume,
                    pitch = packet.pitch,
                )
            }

            else -> false
        }

        if (forwarded) event.isCancelled = true
    }

    private companion object {
        const val POSITION_SCALE = 8f
    }
}
