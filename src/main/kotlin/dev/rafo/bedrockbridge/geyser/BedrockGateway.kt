package dev.rafo.bedrockbridge.geyser

import dev.rafo.bedrockbridge.state.CinematicSession
import java.nio.file.Path
import java.util.UUID

internal interface BedrockGateway {
    val available: Boolean
    val status: String
    val apiVersion: String?

    fun isBedrockPlayer(playerId: UUID): Boolean

    fun openHudSession(playerId: UUID): CinematicSession?

    fun packDirectory(): Path?
}

internal class UnavailableBedrockGateway(
    override val status: String,
) : BedrockGateway {
    override val available: Boolean = false
    override val apiVersion: String? = null

    override fun isBedrockPlayer(playerId: UUID): Boolean = false

    override fun openHudSession(playerId: UUID): CinematicSession? = null

    override fun packDirectory(): Path? = null
}

internal object BedrockGatewayLoader {
    private const val GEYSER_ADAPTER = "dev.rafo.bedrockbridge.geyser.GeyserApiGateway"

    fun load(
        geyserPluginEnabled: Boolean,
        onFailure: (Throwable) -> Unit = {},
    ): BedrockGateway {
        if (!geyserPluginEnabled) {
            return UnavailableBedrockGateway("Geyser-Spigot não está instalado ou ativo")
        }

        return runCatching {
            val adapterClass = Class.forName(GEYSER_ADAPTER)
            val constructor = adapterClass.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance() as BedrockGateway
        }.getOrElse { error ->
            val cause = error.cause ?: error
            onFailure(cause)
            UnavailableBedrockGateway("a API do Geyser não é compatível: ${cause.message ?: cause.javaClass.simpleName}")
        }
    }
}
