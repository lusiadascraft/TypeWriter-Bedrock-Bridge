package dev.rafo.bedrockbridge.velocity.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class HudSessionRegistry {
    private record ActiveSession(long sessionId, RestoringHudSession session) {}

    private final Function<UUID, HudController> controllerFactory;
    private final BiConsumer<UUID, RuntimeException> closeFailureHandler;
    private final Map<UUID, ActiveSession> sessions = new HashMap<>();

    public HudSessionRegistry(Function<UUID, HudController> controllerFactory) {
        this(controllerFactory, (ignoredPlayer, ignoredError) -> {});
    }

    public HudSessionRegistry(
            Function<UUID, HudController> controllerFactory,
            BiConsumer<UUID, RuntimeException> closeFailureHandler
    ) {
        this.controllerFactory = controllerFactory;
        this.closeFailureHandler = closeFailureHandler;
    }

    public synchronized boolean keepActive(UUID playerId, long sessionId) {
        ActiveSession current = sessions.get(playerId);
        if (current != null && current.sessionId == sessionId) {
            current.session.keepActive();
            return true;
        }

        if (current != null) {
            sessions.remove(playerId);
            close(playerId, current);
        }

        HudController controller = controllerFactory.apply(playerId);
        if (controller == null) {
            return false;
        }
        sessions.put(playerId, new ActiveSession(sessionId, new RestoringHudSession(controller)));
        return true;
    }

    public synchronized boolean finish(UUID playerId, long sessionId) {
        ActiveSession current = sessions.get(playerId);
        if (current == null || current.sessionId != sessionId) {
            return false;
        }
        sessions.remove(playerId);
        close(playerId, current);
        return true;
    }

    public synchronized boolean remove(UUID playerId) {
        ActiveSession current = sessions.remove(playerId);
        if (current == null) {
            return false;
        }
        close(playerId, current);
        return true;
    }

    public synchronized int clear() {
        int count = sessions.size();
        var activeSessions = new ArrayList<>(sessions.entrySet());
        sessions.clear();
        activeSessions.forEach(entry -> close(entry.getKey(), entry.getValue()));
        return count;
    }

    public synchronized int size() {
        return sessions.size();
    }

    private void close(UUID playerId, ActiveSession active) {
        try {
            active.session.close();
        } catch (RuntimeException error) {
            closeFailureHandler.accept(playerId, error);
        }
    }
}
