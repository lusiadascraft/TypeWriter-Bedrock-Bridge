package dev.rafo.bedrockbridge.geyser

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BedrockGatewayLoaderTest {
    @Test
    fun `arranca sem carregar classes do Geyser quando o plugin esta ausente`() {
        var failureReported = false

        val gateway = BedrockGatewayLoader.load(
            geyserPluginEnabled = false,
            onFailure = { failureReported = true },
        )

        assertFalse(gateway.available)
        assertFalse(gateway.soundTransportAvailable)
        assertFalse(gateway.isBedrockPlayer(UUID.randomUUID()))
        assertNull(gateway.apiVersion)
        assertFalse(failureReported)
    }
}
