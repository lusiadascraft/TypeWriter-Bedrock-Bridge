package dev.rafo.bedrockbridge.velocity.hud;

import java.util.Set;

public interface HudController {
    Set<HudElement> hiddenElements();

    void hide(Set<HudElement> elements);

    void reset(Set<HudElement> elements);
}
