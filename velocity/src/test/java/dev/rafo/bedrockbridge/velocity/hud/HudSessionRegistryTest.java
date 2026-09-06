package dev.rafo.bedrockbridge.velocity.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HudSessionRegistryTest {
    @Test
    void restoresHudWhenSessionEnds() {
        FakeHudController controller = new FakeHudController();
        HudSessionRegistry registry = new HudSessionRegistry(ignored -> controller);
        UUID playerId = UUID.randomUUID();

        assertTrue(registry.keepActive(playerId, 10));
        assertEquals(Set.copyOf(EnumSet.allOf(HudElement.class)), controller.hiddenElements());
        assertTrue(registry.finish(playerId, 10));
        assertTrue(controller.hiddenElements().isEmpty());
    }

    @Test
    void ignoresLateResetFromPreviousSession() {
        FakeHudController controller = new FakeHudController();
        HudSessionRegistry registry = new HudSessionRegistry(ignored -> controller);
        UUID playerId = UUID.randomUUID();

        registry.keepActive(playerId, 10);
        registry.keepActive(playerId, 11);

        assertFalse(registry.finish(playerId, 10));
        assertEquals(Set.copyOf(EnumSet.allOf(HudElement.class)), controller.hiddenElements());
        assertTrue(registry.finish(playerId, 11));
    }

    @Test
    void preservesElementsHiddenBeforeCinematic() {
        FakeHudController controller = new FakeHudController(Set.of(HudElement.HOTBAR));
        HudSessionRegistry registry = new HudSessionRegistry(ignored -> controller);
        UUID playerId = UUID.randomUUID();

        registry.keepActive(playerId, 1);
        registry.finish(playerId, 1);

        assertEquals(Set.of(HudElement.HOTBAR), controller.hiddenElements());
    }

    @Test
    void clearsEverySessionWhenOneHudRestoreFails() {
        UUID failingPlayer = UUID.randomUUID();
        UUID healthyPlayer = UUID.randomUUID();
        FakeHudController healthyController = new FakeHudController();
        var failures = new ArrayList<UUID>();
        HudSessionRegistry registry = new HudSessionRegistry(
                playerId -> playerId.equals(failingPlayer)
                        ? new FailingHudController()
                        : healthyController,
                (playerId, ignored) -> failures.add(playerId)
        );

        registry.keepActive(failingPlayer, 1);
        registry.keepActive(healthyPlayer, 2);

        assertEquals(2, registry.clear());
        assertEquals(0, registry.size());
        assertEquals(Set.of(failingPlayer), Set.copyOf(failures));
        assertTrue(healthyController.hiddenElements().isEmpty());
    }

    private static final class FakeHudController implements HudController {
        private final EnumSet<HudElement> hidden;

        private FakeHudController() {
            this(Set.of());
        }

        private FakeHudController(Set<HudElement> hidden) {
            this.hidden = hidden.isEmpty()
                    ? EnumSet.noneOf(HudElement.class)
                    : EnumSet.copyOf(hidden);
        }

        @Override
        public Set<HudElement> hiddenElements() {
            return Set.copyOf(hidden);
        }

        @Override
        public void hide(Set<HudElement> elements) {
            hidden.addAll(elements);
        }

        @Override
        public void reset(Set<HudElement> elements) {
            hidden.removeAll(elements);
        }
    }

    private static final class FailingHudController implements HudController {
        private final EnumSet<HudElement> hidden = EnumSet.noneOf(HudElement.class);

        @Override
        public Set<HudElement> hiddenElements() {
            return Set.copyOf(hidden);
        }

        @Override
        public void hide(Set<HudElement> elements) {
            hidden.addAll(elements);
        }

        @Override
        public void reset(Set<HudElement> elements) {
            throw new IllegalStateException("falha de teste");
        }
    }
}
