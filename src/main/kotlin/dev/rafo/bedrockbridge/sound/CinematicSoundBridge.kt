package dev.rafo.bedrockbridge.sound

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal class CinematicSoundBridge(
    private val isCinematicActive: (UUID) -> Boolean,
    private val resolveSound: (String) -> String?,
    private val sendSound: (UUID, BedrockSoundRequest) -> Boolean,
) {
    private val forwardedCounter = AtomicLong()

    val forwardedSounds: Long
        get() = forwardedCounter.get()

    fun forward(
        playerId: UUID,
        javaIdentifier: String,
        position: SoundPosition,
        volume: Float,
        pitch: Float,
    ): Boolean {
        if (!isCinematicActive(playerId)) return false
        val bedrockIdentifier = resolveSound(javaIdentifier) ?: return false
        val request = BedrockSoundRequest(
            identifier = bedrockIdentifier,
            position = position,
            volume = volume,
            pitch = pitch,
        )

        val sent = sendSound(playerId, request)
        if (sent) forwardedCounter.incrementAndGet()
        return sent
    }
}
