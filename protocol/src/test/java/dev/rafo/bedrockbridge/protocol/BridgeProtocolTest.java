package dev.rafo.bedrockbridge.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BridgeProtocolTest {
    @Test
    void roundTripsEveryMessage() {
        List<BridgeMessage> messages = List.of(
                new BridgeMessage.Hello(),
                new BridgeMessage.HudHide(42),
                new BridgeMessage.HudReset(42),
                new BridgeMessage.PlaySound(
                        "lusiadascraft:intro",
                        new SoundPosition.Absolute(1.5f, 2.5f, 3.5f),
                        0.8f,
                        1.2f
                ),
                new BridgeMessage.PlaySound(
                        "lusiadascraft:narrador",
                        new SoundPosition.Entity(37),
                        1f,
                        1f
                ),
                new BridgeMessage.Welcome(99, 2, 3, true),
                new BridgeMessage.CatalogChunk(99, 0, 2, List.of("um", "dois"))
        );

        for (BridgeMessage message : messages) {
            assertEquals(message, BridgeProtocol.decode(BridgeProtocol.encode(message)));
        }
    }

    @Test
    void rejectsInvalidMessages() {
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decode(new byte[] {1, 2, 3}));
    }

    @Test
    void splitsLargeCatalogsWithinTheTransportLimit() {
        String longName = "som." + "x".repeat(400);
        List<String> definitions = java.util.stream.IntStream.range(0, 500)
                .mapToObj(index -> longName + index)
                .toList();

        List<List<String>> chunks = BridgeProtocol.chunkCatalog(definitions);

        assertTrue(chunks.size() > 1);
        int totalChunks = chunks.size();
        for (int index = 0; index < totalChunks; index++) {
            byte[] payload = BridgeProtocol.encode(new BridgeMessage.CatalogChunk(1, index, totalChunks, chunks.get(index)));
            assertTrue(payload.length <= BridgeProtocol.MAX_PAYLOAD_BYTES);
        }
    }
}
