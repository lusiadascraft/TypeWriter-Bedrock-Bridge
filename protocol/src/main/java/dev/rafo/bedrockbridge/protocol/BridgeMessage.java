package dev.rafo.bedrockbridge.protocol;

import java.util.List;

public sealed interface BridgeMessage {
    record Hello() implements BridgeMessage {}

    record HudHide(long sessionId) implements BridgeMessage {}

    record HudReset(long sessionId) implements BridgeMessage {}

    record PlaySound(
            String identifier,
            SoundPosition position,
            float volume,
            float pitch
    ) implements BridgeMessage {}

    record Welcome(
            long catalogGeneration,
            int totalChunks,
            int soundDefinitions,
            boolean soundTransportAvailable
    ) implements BridgeMessage {}

    record CatalogChunk(
            long catalogGeneration,
            int index,
            int totalChunks,
            List<String> definitions
    ) implements BridgeMessage {
        public CatalogChunk {
            definitions = List.copyOf(definitions);
        }
    }
}
