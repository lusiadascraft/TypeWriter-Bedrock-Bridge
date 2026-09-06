package dev.rafo.bedrockbridge.velocity.sound;

import dev.rafo.bedrockbridge.protocol.SoundPosition;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.api.connection.GeyserConnection;

public final class GeyserSoundSender {
    private static final String PLAY_SOUND_PACKET = "org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket";

    private final Class<?> packetClass;
    private final java.lang.reflect.Constructor<?> packetConstructor;
    private final Method setSound;
    private final Method setPosition;
    private final Method setVolume;
    private final Method setPitch;
    private final Map<Class<?>, Method> sendMethods = new ConcurrentHashMap<>();

    public GeyserSoundSender(ClassLoader classLoader) throws ReflectiveOperationException {
        packetClass = Class.forName(PLAY_SOUND_PACKET, true, classLoader);
        packetConstructor = packetClass.getDeclaredConstructor();
        setSound = packetClass.getMethod("setSound", String.class);
        setPosition = packetClass.getMethod("setPosition", Vector3f.class);
        setVolume = packetClass.getMethod("setVolume", float.class);
        setPitch = packetClass.getMethod("setPitch", float.class);
    }

    public boolean send(
            GeyserConnection connection,
            String identifier,
            SoundPosition position,
            float volume,
            float pitch
    ) throws ReflectiveOperationException {
        Vector3f resolvedPosition = resolvePosition(connection, position);
        if (resolvedPosition == null) {
            return false;
        }

        Object packet = packetConstructor.newInstance();
        setSound.invoke(packet, identifier);
        setPosition.invoke(packet, resolvedPosition);
        setVolume.invoke(packet, volume);
        setPitch.invoke(packet, pitch);
        sendMethod(connection.getClass()).invoke(connection, packet);
        return true;
    }

    private Method sendMethod(Class<?> connectionClass) {
        return sendMethods.computeIfAbsent(connectionClass, type -> {
            for (Method method : type.getMethods()) {
                if (method.getName().equals("sendUpstreamPacket")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(packetClass)) {
                    return method;
                }
            }
            throw new IllegalStateException("GeyserSession#sendUpstreamPacket não foi encontrado");
        });
    }

    private Vector3f resolvePosition(GeyserConnection connection, SoundPosition position) {
        return switch (position) {
            case SoundPosition.Absolute absolute -> Vector3f.from(absolute.x(), absolute.y(), absolute.z());
            case SoundPosition.Entity entity -> entityPosition(connection, entity.javaEntityId());
        };
    }

    private Vector3f entityPosition(GeyserConnection connection, int javaEntityId) {
        try {
            Object entityCache = connection.getClass().getMethod("getEntityCache").invoke(connection);
            Object entity = entityCache.getClass()
                    .getMethod("getEntityByJavaId", int.class)
                    .invoke(entityCache, javaEntityId);
            if (entity == null) {
                return null;
            }
            Object position = entity.getClass().getMethod("getPosition").invoke(entity);
            return position instanceof Vector3f vector ? vector : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
