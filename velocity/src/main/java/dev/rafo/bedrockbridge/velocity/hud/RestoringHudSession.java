package dev.rafo.bedrockbridge.velocity.hud;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RestoringHudSession implements AutoCloseable {
    private static final Set<HudElement> CINEMATIC_ELEMENTS = Set.copyOf(EnumSet.allOf(HudElement.class));

    private final HudController controller;
    private final Set<HudElement> originallyHidden;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RestoringHudSession(HudController controller) {
        this.controller = controller;
        EnumSet<HudElement> original = copyOf(controller.hiddenElements());
        original.retainAll(CINEMATIC_ELEMENTS);
        originallyHidden = Set.copyOf(original);
        keepActive();
    }

    public void keepActive() {
        if (closed.get()) {
            return;
        }
        EnumSet<HudElement> missing = EnumSet.copyOf(CINEMATIC_ELEMENTS);
        missing.removeAll(controller.hiddenElements());
        if (!missing.isEmpty()) {
            controller.hide(missing);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        EnumSet<HudElement> addedByBridge = EnumSet.copyOf(CINEMATIC_ELEMENTS);
        addedByBridge.removeAll(originallyHidden);
        try {
            if (!addedByBridge.isEmpty()) {
                controller.reset(addedByBridge);
            }
        } finally {
            EnumSet<HudElement> hiddenBefore = copyOf(originallyHidden);
            hiddenBefore.removeAll(controller.hiddenElements());
            if (!hiddenBefore.isEmpty()) {
                controller.hide(hiddenBefore);
            }
        }
    }

    private static EnumSet<HudElement> copyOf(Set<HudElement> elements) {
        return elements.isEmpty() ? EnumSet.noneOf(HudElement.class) : EnumSet.copyOf(elements);
    }
}
