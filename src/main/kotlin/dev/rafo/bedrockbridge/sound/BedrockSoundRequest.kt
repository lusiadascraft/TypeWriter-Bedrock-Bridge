package dev.rafo.bedrockbridge.sound

internal data class BedrockSoundRequest(
    val identifier: String,
    val position: SoundPosition,
    val volume: Float,
    val pitch: Float,
)

internal sealed interface SoundPosition {
    data class Absolute(
        val x: Float,
        val y: Float,
        val z: Float,
    ) : SoundPosition

    data class Entity(
        val javaEntityId: Int,
        val fallback: Absolute,
    ) : SoundPosition
}
