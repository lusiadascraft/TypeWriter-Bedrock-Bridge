package dev.rafo.bedrockbridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.rafo.bedrockbridge.protocol.BridgeMessage;
import dev.rafo.bedrockbridge.protocol.BridgeProtocol;
import dev.rafo.bedrockbridge.velocity.hud.GeyserHudController;
import dev.rafo.bedrockbridge.velocity.hud.HudSessionRegistry;
import dev.rafo.bedrockbridge.velocity.sound.BedrockSoundCatalog;
import dev.rafo.bedrockbridge.velocity.sound.GeyserSoundSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.slf4j.Logger;

@Plugin(
        id = "bedrockbridge",
        name = "BedrockBridge-Velocity",
        version = "0.1.0-SNAPSHOT",
        description = "Liga as cinematics Typewriter do Paper ao Geyser no Velocity.",
        authors = {"Rafael"},
        dependencies = {@Dependency(id = "geyser")}
)
public final class VelocityBedrockBridge {
    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(BridgeProtocol.CHANNEL);

    private final ProxyServer proxy;
    private final Logger logger;

    private GeyserApi geyser;
    private HudSessionRegistry hudSessions;
    private BedrockSoundCatalog soundCatalog;
    private volatile GeyserSoundSender soundSender;
    private long catalogGeneration;
    private List<byte[]> catalogMessages = List.of();

    @Inject
    public VelocityBedrockBridge(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        geyser = GeyserApi.api();
        hudSessions = new HudSessionRegistry(
                playerId -> {
                    GeyserConnection connection = geyser.connectionByUuid(playerId);
                    return connection == null ? null : new GeyserHudController(connection.camera());
                },
                (playerId, error) -> logger.warn(
                        "Não foi possível restaurar o HUD de {}: {}",
                        playerId,
                        error.getMessage()
                )
        );
        soundCatalog = BedrockSoundCatalog.load(geyser.packDirectory(), (source, error) ->
                logger.warn("Não foi possível ler o pack '{}': {}", source, error.getMessage()));
        soundSender = createSoundSender();
        prepareCatalogMessages();

        proxy.getAllPlayers().forEach(this::welcomeIfBedrock);
        logger.info(
                "BedrockBridge-Velocity ativo: {} som(ns), áudio direto {}.",
                soundCatalog.size(),
                soundSender == null ? "indisponível" : "disponível"
        );
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection backend)
                || !(event.getTarget() instanceof Player player)
                || !backend.getPlayer().equals(player)
                || player.getCurrentServer().filter(backend::equals).isEmpty()) {
            return;
        }

        BridgeMessage message;
        try {
            message = BridgeProtocol.decode(event.getData());
        } catch (IllegalArgumentException error) {
            logger.warn("Mensagem inválida recebida de {}: {}", backend.getServerInfo().getName(), error.getMessage());
            return;
        }

        GeyserConnection connection = geyser.connectionByUuid(player.getUniqueId());
        if (connection == null) {
            return;
        }

        try {
            switch (message) {
                case BridgeMessage.Hello ignored -> sendWelcome(backend);
                case BridgeMessage.HudHide hide ->
                        hudSessions.keepActive(player.getUniqueId(), hide.sessionId());
                case BridgeMessage.HudReset reset ->
                        hudSessions.finish(player.getUniqueId(), reset.sessionId());
                case BridgeMessage.PlaySound sound -> playSound(connection, sound);
                default -> {
                    // As respostas pertencem ao sentido proxy -> Paper.
                }
            }
        } catch (Throwable error) {
            logger.warn("Falha ao processar uma mensagem para {}: {}", player.getUsername(), error.getMessage());
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (event.getPreviousServer() != null) {
            hudSessions.remove(event.getPlayer().getUniqueId());
        }
        welcomeIfBedrock(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        hudSessions.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        int restored = hudSessions == null ? 0 : hudSessions.clear();
        proxy.getChannelRegistrar().unregister(CHANNEL);
        logger.info("BedrockBridge-Velocity desligado; {} estado(s) restaurado(s).", restored);
    }

    private void welcomeIfBedrock(Player player) {
        if (geyser.connectionByUuid(player.getUniqueId()) == null) {
            return;
        }
        player.getCurrentServer().ifPresent(this::sendWelcome);
    }

    private void sendWelcome(ServerConnection backend) {
        backend.sendPluginMessage(
                CHANNEL,
                BridgeProtocol.encode(new BridgeMessage.Welcome(
                        catalogGeneration,
                        catalogMessages.size(),
                        soundCatalog.size(),
                        soundSender != null
                ))
        );
        catalogMessages.forEach(payload -> backend.sendPluginMessage(CHANNEL, payload));
    }

    private void playSound(GeyserConnection connection, BridgeMessage.PlaySound sound) {
        if (soundSender == null) {
            return;
        }
        String identifier = soundCatalog.resolve(sound.identifier());
        if (identifier == null) {
            return;
        }

        try {
            soundSender.send(connection, identifier, sound.position(), sound.volume(), sound.pitch());
        } catch (ReflectiveOperationException | RuntimeException error) {
            soundSender = null;
            logger.warn("Áudio direto desativado depois de uma falha: {}", error.getMessage());
            proxy.getAllPlayers().forEach(this::welcomeIfBedrock);
        }
    }

    private GeyserSoundSender createSoundSender() {
        try {
            return new GeyserSoundSender(geyser.getClass().getClassLoader());
        } catch (ReflectiveOperationException error) {
            logger.warn("Áudio direto desativado: {}", error.getMessage());
            return null;
        }
    }

    private void prepareCatalogMessages() {
        catalogGeneration = ThreadLocalRandom.current().nextLong();
        List<List<String>> chunks = BridgeProtocol.chunkCatalog(soundCatalog.definitions());
        List<byte[]> messages = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            messages.add(BridgeProtocol.encode(new BridgeMessage.CatalogChunk(
                    catalogGeneration,
                    index,
                    chunks.size(),
                    chunks.get(index)
            )));
        }
        catalogMessages = List.copyOf(messages);
    }
}
