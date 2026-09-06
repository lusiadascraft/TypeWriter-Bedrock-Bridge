package dev.rafo.bedrockbridge

import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.events.AsyncCinematicEndEvent
import com.typewritermc.engine.paper.events.AsyncCinematicStartEvent
import com.typewritermc.engine.paper.events.AsyncCinematicTickEvent
import com.typewritermc.engine.paper.events.TypewriterUnloadEvent
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.plugin
import dev.rafo.bedrockbridge.geyser.BedrockGateway
import dev.rafo.bedrockbridge.geyser.BedrockGatewayLoader
import dev.rafo.bedrockbridge.geyser.FloodgateVelocityGatewayLoader
import dev.rafo.bedrockbridge.sound.BedrockSoundCatalog
import dev.rafo.bedrockbridge.sound.PacketEventsSoundCompatibility
import dev.rafo.bedrockbridge.state.CinematicPlayerRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

@Singleton
internal object BedrockBridge : Initializable, Listener {
    const val NAME = "BedrockBridge"

    private data class Runtime(
        val gateway: BedrockGateway,
        val players: CinematicPlayerRegistry,
        val sound: PacketEventsSoundCompatibility?,
        val localSoundCatalog: BedrockSoundCatalog,
    )

    data class Status(
        val geyserAvailable: Boolean,
        val geyserStatus: String,
        val geyserApiVersion: String?,
        val activeCinematics: Int,
        val soundDefinitions: Int,
        val soundTransportAvailable: Boolean,
        val soundStatus: String,
        val forwardedSounds: Long,
    )

    data class PlayerStatus(
        val bedrockPlayer: Boolean,
        val cinematicActive: Boolean,
    )

    @Volatile
    private var runtime: Runtime? = null

    override suspend fun initialize() {
        cleanup()

        val geyserEnabled = plugin.server.pluginManager.isPluginEnabled(GEYSER_PLUGIN_NAME)
        val gateway = if (geyserEnabled) {
            BedrockGatewayLoader.load(true) { error ->
                logger.warning("$NAME: não foi possível ligar à API do Geyser: ${error.message}")
            }
        } else {
            val floodgatePlugin = plugin.server.pluginManager.getPlugin(FLOODGATE_PLUGIN_NAME)
                ?.takeIf { it.isEnabled }
            FloodgateVelocityGatewayLoader.load(floodgatePlugin, plugin) { error ->
                logger.warning("$NAME: não foi possível ligar ao Floodgate/Velocity: ${error.message}")
            }
        }
        val players = CinematicPlayerRegistry(
            onFailure = { operation, error ->
                logger.warning("$NAME: falha ao $operation: ${error.message}")
            },
            sessionFactory = gateway::openHudSession,
        )
        val packDirectory = runCatching(gateway::packDirectory)
            .onFailure { logger.warning("$NAME: não foi possível obter o diretório de packs: ${it.message}") }
            .getOrNull()
        val catalog = BedrockSoundCatalog.load(packDirectory) { source, error ->
            logger.warning("$NAME: não foi possível ler o pack '$source': ${error.message}")
        }
        val sound = if (gateway.soundTransportAvailable) {
            runCatching { PacketEventsSoundCompatibility(gateway, players, catalog) }
                .onFailure { logger.warning("$NAME: compatibilidade de som desativada: ${it.message}") }
                .getOrNull()
        } else {
            null
        }

        runtime = Runtime(gateway, players, sound, catalog)
        plugin.server.pluginManager.registerEvents(this, plugin)

        val version = gateway.apiVersion?.let { " (API $it)" }.orEmpty()
        logger.info("$NAME: ${gateway.status}$version.")
        logger.info(
            "$NAME: ${gateway.soundDefinitionCount(catalog)} definição(ões) de som Bedrock; " +
                "transporte ${gateway.soundStatus}; listener ${if (sound == null) "inativo" else "ativo"}.",
        )
    }

    override suspend fun shutdown() {
        val restored = cleanup()
        logger.info("$NAME: extensão desligada; $restored estado(s) restaurado(s).")
    }

    fun status(): Status {
        val current = runtime
        return Status(
            geyserAvailable = current?.gateway?.available == true,
            geyserStatus = current?.gateway?.status ?: "extensão ainda não inicializada",
            geyserApiVersion = current?.gateway?.apiVersion,
            activeCinematics = current?.players?.activeCount ?: 0,
            soundDefinitions = current?.gateway?.soundDefinitionCount(current.localSoundCatalog) ?: 0,
            soundTransportAvailable = current?.gateway?.soundTransportAvailable == true,
            soundStatus = current?.gateway?.soundStatus ?: "extensão ainda não inicializada",
            forwardedSounds = current?.sound?.forwardedSounds ?: 0,
        )
    }

    fun playerStatus(playerId: UUID): PlayerStatus {
        val current = runtime
        val isBedrock = runCatching { current?.gateway?.isBedrockPlayer(playerId) == true }
            .onFailure { logger.warning("$NAME: falha ao detetar o jogador $playerId: ${it.message}") }
            .getOrDefault(false)

        return PlayerStatus(
            bedrockPlayer = isBedrock,
            cinematicActive = current?.players?.isActive(playerId) == true,
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun onCinematicStart(event: AsyncCinematicStartEvent) {
        runtime?.players?.start(event.player.uniqueId, event.pageId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun onCinematicTick(event: AsyncCinematicTickEvent) {
        runtime?.players?.tick(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun onCinematicEnd(event: AsyncCinematicEndEvent) {
        runtime?.players?.finish(event.player.uniqueId, event.pageId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun onPlayerQuit(event: PlayerQuitEvent) {
        runtime?.players?.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun onTypewriterUnload(event: TypewriterUnloadEvent) {
        cleanup()
    }

    private fun cleanup(): Int {
        HandlerList.unregisterAll(this)
        val current = runtime
        runtime = null
        current?.sound?.close()
        val restored = current?.players?.clear() ?: 0
        current?.gateway?.close()
        return restored
    }

    private const val GEYSER_PLUGIN_NAME = "Geyser-Spigot"
    private const val FLOODGATE_PLUGIN_NAME = "floodgate"
}
