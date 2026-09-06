package dev.rafo.bedrockbridge.transport

import dev.rafo.bedrockbridge.protocol.BridgeMessage
import dev.rafo.bedrockbridge.protocol.BridgeProtocol
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class VelocityBridgeTransport(
    private val plugin: Plugin,
    private val onFailure: (operation: String, error: Throwable) -> Unit,
) : PluginMessageListener, Listener, AutoCloseable {
    private val readyPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val soundReadyPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val remoteCatalog = RemoteSoundCatalog()
    private val closed = AtomicBoolean(false)

    val readyCount: Int
        get() = readyPlayers.size

    val soundDefinitions: Int
        get() = remoteCatalog.size

    val soundReadyCount: Int
        get() = soundReadyPlayers.size

    val catalogReady: Boolean
        get() = remoteCatalog.synchronized

    fun start() {
        try {
            plugin.server.messenger.registerOutgoingPluginChannel(plugin, BridgeProtocol.CHANNEL)
            plugin.server.messenger.registerIncomingPluginChannel(plugin, BridgeProtocol.CHANNEL, this)
            plugin.server.pluginManager.registerEvents(this, plugin)
            plugin.server.onlinePlayers.forEach(::announce)
        } catch (error: Throwable) {
            closed.set(true)
            HandlerList.unregisterAll(this)
            plugin.server.messenger.unregisterIncomingPluginChannel(plugin, BridgeProtocol.CHANNEL, this)
            plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, BridgeProtocol.CHANNEL)
            throw error
        }
    }

    fun isReady(playerId: UUID): Boolean = playerId in readyPlayers

    fun resolveSound(javaIdentifier: String): String? = remoteCatalog.resolve(javaIdentifier)

    fun send(playerId: UUID, message: BridgeMessage): Boolean {
        if (closed.get() || playerId !in readyPlayers) return false
        return dispatch(playerId, message)
    }

    fun sendSound(playerId: UUID, message: BridgeMessage.PlaySound): Boolean {
        if (closed.get() || playerId !in soundReadyPlayers) return false
        return dispatch(playerId, message)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != BridgeProtocol.CHANNEL || closed.get()) return

        runCatching { BridgeProtocol.decode(message) }
            .onFailure { onFailure("ler uma resposta do proxy", it) }
            .onSuccess { response ->
                when (response) {
                    is BridgeMessage.Welcome -> {
                        runCatching { remoteCatalog.begin(response) }
                            .onFailure { onFailure("iniciar o catálogo remoto", it) }
                            .onSuccess {
                                readyPlayers += player.uniqueId
                                if (response.soundTransportAvailable) {
                                    soundReadyPlayers += player.uniqueId
                                } else {
                                    soundReadyPlayers -= player.uniqueId
                                }
                            }
                    }

                    is BridgeMessage.CatalogChunk -> {
                        runCatching { remoteCatalog.accept(response) }
                            .onFailure { onFailure("receber o catálogo remoto", it) }
                    }

                    else -> Unit
                }
            }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable { announce(event.player) })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun onPlayerQuit(event: PlayerQuitEvent) {
        readyPlayers -= event.player.uniqueId
        soundReadyPlayers -= event.player.uniqueId
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        HandlerList.unregisterAll(this)
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, BridgeProtocol.CHANNEL, this)
        readyPlayers.clear()
        soundReadyPlayers.clear()
    }

    private fun announce(player: Player) {
        if (closed.get()) return
        dispatch(player.uniqueId, BridgeMessage.Hello())
    }

    private fun dispatch(playerId: UUID, message: BridgeMessage): Boolean {
        val payload = runCatching { BridgeProtocol.encode(message) }
            .onFailure { onFailure("preparar uma mensagem para o proxy", it) }
            .getOrNull()
            ?: return false

        val send = Runnable {
            val player = plugin.server.getPlayer(playerId)
            if (player != null && player.isOnline) {
                runCatching { player.sendPluginMessage(plugin, BridgeProtocol.CHANNEL, payload) }
                    .onFailure { onFailure("enviar uma mensagem para o proxy", it) }
            }
        }

        if (plugin.server.isPrimaryThread) {
            send.run()
        } else {
            plugin.server.scheduler.runTask(plugin, send)
        }
        return true
    }
}
