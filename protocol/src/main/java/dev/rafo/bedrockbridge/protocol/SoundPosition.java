package dev.rafo.bedrockbridge.protocol;

public sealed interface SoundPosition {
    record Absolute(float x, float y, float z) implements SoundPosition {}

    record Entity(int javaEntityId) implements SoundPosition {}
}
