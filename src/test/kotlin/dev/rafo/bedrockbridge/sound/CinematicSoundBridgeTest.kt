package dev.rafo.bedrockbridge.sound

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CinematicSoundBridgeTest {
    @Test
    fun `encaminha um som conhecido durante uma cinematic`() {
        val playerId = UUID.randomUUID()
        var sentRequest: BedrockSoundRequest? = null
        val bridge = CinematicSoundBridge(
            isCinematicActive = { it == playerId },
            resolveSound = { "bedrock.$it" },
            sendSound = { _, request ->
                sentRequest = request
                true
            },
        )

        val forwarded = bridge.forward(
            playerId,
            "story:intro",
            SoundPosition.Absolute(1f, 2f, 3f),
            volume = 0.8f,
            pitch = 1.2f,
        )

        assertTrue(forwarded)
        assertEquals("bedrock.story:intro", sentRequest?.identifier)
        assertEquals(1, bridge.forwardedSounds)
    }

    @Test
    fun `não toca sons fora de uma cinematic Bedrock`() {
        var sendCalls = 0
        val bridge = CinematicSoundBridge(
            isCinematicActive = { false },
            resolveSound = { it },
            sendSound = { _, _ -> sendCalls++; true },
        )

        val forwarded = bridge.forward(
            UUID.randomUUID(),
            "story:intro",
            SoundPosition.Absolute(0f, 0f, 0f),
            1f,
            1f,
        )

        assertFalse(forwarded)
        assertEquals(0, sendCalls)
    }

    @Test
    fun `mantém o pacote original quando o som não existe no pack`() {
        var sendCalls = 0
        val bridge = CinematicSoundBridge(
            isCinematicActive = { true },
            resolveSound = { null },
            sendSound = { _, _ -> sendCalls++; true },
        )

        val forwarded = bridge.forward(
            UUID.randomUUID(),
            "story:missing",
            SoundPosition.Entity(42),
            1f,
            1f,
        )

        assertFalse(forwarded)
        assertEquals(0, sendCalls)
    }

    @Test
    fun `mantém o pacote original quando o transporte Geyser falha`() {
        val bridge = CinematicSoundBridge(
            isCinematicActive = { true },
            resolveSound = { it },
            sendSound = { _, _ -> false },
        )

        val forwarded = bridge.forward(
            UUID.randomUUID(),
            "story:intro",
            SoundPosition.Absolute(0f, 0f, 0f),
            1f,
            1f,
        )

        assertFalse(forwarded)
        assertEquals(0, bridge.forwardedSounds)
    }
}
