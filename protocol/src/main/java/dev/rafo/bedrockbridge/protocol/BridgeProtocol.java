package dev.rafo.bedrockbridge.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class BridgeProtocol {
    public static final String CHANNEL = "lusiadascraft:bedrockbridge";
    public static final int MAX_PAYLOAD_BYTES = 24 * 1024;

    private static final int MAGIC = 0x42425247;
    private static final short VERSION = 1;
    private static final int MAX_STRING_BYTES = 16 * 1024;
    private static final int MAX_CATALOG_DEFINITIONS = 10_000;

    private static final byte HELLO = 1;
    private static final byte HUD_HIDE = 2;
    private static final byte HUD_RESET = 3;
    private static final byte PLAY_SOUND = 4;
    private static final byte WELCOME = 5;
    private static final byte CATALOG_CHUNK = 6;

    private static final byte ABSOLUTE_POSITION = 1;
    private static final byte ENTITY_POSITION = 2;

    private BridgeProtocol() {}

    public static byte[] encode(BridgeMessage message) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeShort(VERSION);

                switch (message) {
                    case BridgeMessage.Hello ignored -> output.writeByte(HELLO);
                    case BridgeMessage.HudHide hide -> {
                        output.writeByte(HUD_HIDE);
                        output.writeLong(hide.sessionId());
                    }
                    case BridgeMessage.HudReset reset -> {
                        output.writeByte(HUD_RESET);
                        output.writeLong(reset.sessionId());
                    }
                    case BridgeMessage.PlaySound sound -> {
                        output.writeByte(PLAY_SOUND);
                        writeString(output, sound.identifier());
                        writePosition(output, sound.position());
                        output.writeFloat(sound.volume());
                        output.writeFloat(sound.pitch());
                    }
                    case BridgeMessage.Welcome welcome -> {
                        output.writeByte(WELCOME);
                        output.writeLong(welcome.catalogGeneration());
                        output.writeInt(welcome.totalChunks());
                        output.writeInt(welcome.soundDefinitions());
                    }
                    case BridgeMessage.CatalogChunk chunk -> {
                        output.writeByte(CATALOG_CHUNK);
                        output.writeLong(chunk.catalogGeneration());
                        output.writeInt(chunk.index());
                        output.writeInt(chunk.totalChunks());
                        output.writeInt(chunk.definitions().size());
                        for (String definition : chunk.definitions()) {
                            writeString(output, definition);
                        }
                    }
                }
            }

            byte[] payload = bytes.toByteArray();
            if (payload.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("A mensagem excede o limite de " + MAX_PAYLOAD_BYTES + " bytes");
            }
            return payload;
        } catch (IOException error) {
            throw new IllegalStateException("Não foi possível codificar a mensagem", error);
        }
    }

    public static BridgeMessage decode(byte[] payload) {
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("A mensagem excede o limite de " + MAX_PAYLOAD_BYTES + " bytes");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Assinatura do protocolo inválida");
            }
            short version = input.readShort();
            if (version != VERSION) {
                throw new IllegalArgumentException("Versão de protocolo incompatível: " + version);
            }

            BridgeMessage message = switch (input.readUnsignedByte()) {
                case HELLO -> new BridgeMessage.Hello();
                case HUD_HIDE -> new BridgeMessage.HudHide(input.readLong());
                case HUD_RESET -> new BridgeMessage.HudReset(input.readLong());
                case PLAY_SOUND -> new BridgeMessage.PlaySound(
                        readString(input),
                        readPosition(input),
                        input.readFloat(),
                        input.readFloat()
                );
                case WELCOME -> new BridgeMessage.Welcome(input.readLong(), input.readInt(), input.readInt());
                case CATALOG_CHUNK -> readCatalogChunk(input);
                default -> throw new IllegalArgumentException("Tipo de mensagem desconhecido");
            };

            if (input.available() != 0) {
                throw new IllegalArgumentException("A mensagem contém dados inesperados");
            }
            return message;
        } catch (EOFException error) {
            throw new IllegalArgumentException("Mensagem incompleta", error);
        } catch (IOException error) {
            throw new IllegalArgumentException("Não foi possível ler a mensagem", error);
        }
    }

    public static List<List<String>> chunkCatalog(Collection<String> definitions) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentBytes = catalogChunkHeaderBytes();

        for (String definition : definitions) {
            int entryBytes = Integer.BYTES + definition.getBytes(StandardCharsets.UTF_8).length;
            if (entryBytes + catalogChunkHeaderBytes() > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Identificador de som demasiado comprido");
            }
            if (!current.isEmpty() && currentBytes + entryBytes > MAX_PAYLOAD_BYTES) {
                chunks.add(List.copyOf(current));
                current.clear();
                currentBytes = catalogChunkHeaderBytes();
            }
            current.add(definition);
            currentBytes += entryBytes;
        }

        if (!current.isEmpty()) {
            chunks.add(List.copyOf(current));
        }
        return List.copyOf(chunks);
    }

    private static BridgeMessage.CatalogChunk readCatalogChunk(DataInputStream input) throws IOException {
        long generation = input.readLong();
        int index = input.readInt();
        int totalChunks = input.readInt();
        int size = input.readInt();
        if (totalChunks < 0 || index < 0 || index >= totalChunks) {
            throw new IllegalArgumentException("Índice de catálogo inválido");
        }
        if (size < 0 || size > MAX_CATALOG_DEFINITIONS) {
            throw new IllegalArgumentException("Tamanho de catálogo inválido");
        }

        List<String> definitions = new ArrayList<>(size);
        for (int indexInChunk = 0; indexInChunk < size; indexInChunk++) {
            definitions.add(readString(input));
        }
        return new BridgeMessage.CatalogChunk(generation, index, totalChunks, definitions);
    }

    private static void writePosition(DataOutputStream output, SoundPosition position) throws IOException {
        switch (position) {
            case SoundPosition.Absolute absolute -> {
                output.writeByte(ABSOLUTE_POSITION);
                output.writeFloat(absolute.x());
                output.writeFloat(absolute.y());
                output.writeFloat(absolute.z());
            }
            case SoundPosition.Entity entity -> {
                output.writeByte(ENTITY_POSITION);
                output.writeInt(entity.javaEntityId());
            }
        }
    }

    private static SoundPosition readPosition(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case ABSOLUTE_POSITION -> new SoundPosition.Absolute(
                    input.readFloat(),
                    input.readFloat(),
                    input.readFloat()
            );
            case ENTITY_POSITION -> new SoundPosition.Entity(input.readInt());
            default -> throw new IllegalArgumentException("Tipo de posição desconhecido");
        };
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Texto demasiado comprido");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > input.available()) {
            throw new IllegalArgumentException("Comprimento de texto inválido");
        }
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static int catalogChunkHeaderBytes() {
        return Integer.BYTES + Short.BYTES + Byte.BYTES + Long.BYTES + Integer.BYTES * 3;
    }
}
