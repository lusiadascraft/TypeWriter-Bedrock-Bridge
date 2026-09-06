package dev.rafo.bedrockbridge.velocity.hud;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.geysermc.geyser.api.bedrock.camera.CameraData;
import org.geysermc.geyser.api.bedrock.camera.GuiElement;

public final class GeyserHudController implements HudController {
    private static final Map<HudElement, GuiElement> TO_GEYSER = createMapping();
    private static final Map<GuiElement, HudElement> FROM_GEYSER = TO_GEYSER.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private final CameraData camera;

    public GeyserHudController(CameraData camera) {
        this.camera = camera;
    }

    @Override
    public Set<HudElement> hiddenElements() {
        return camera.hiddenElements().stream()
                .map(FROM_GEYSER::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void hide(Set<HudElement> elements) {
        camera.hideElement(elements.stream().map(TO_GEYSER::get).toArray(GuiElement[]::new));
    }

    @Override
    public void reset(Set<HudElement> elements) {
        camera.resetElement(elements.stream().map(TO_GEYSER::get).toArray(GuiElement[]::new));
    }

    private static Map<HudElement, GuiElement> createMapping() {
        Map<HudElement, GuiElement> mapping = new EnumMap<>(HudElement.class);
        mapping.put(HudElement.PAPER_DOLL, GuiElement.PAPER_DOLL);
        mapping.put(HudElement.ARMOR, GuiElement.ARMOR);
        mapping.put(HudElement.TOOL_TIPS, GuiElement.TOOL_TIPS);
        mapping.put(HudElement.TOUCH_CONTROLS, GuiElement.TOUCH_CONTROLS);
        mapping.put(HudElement.CROSSHAIR, GuiElement.CROSSHAIR);
        mapping.put(HudElement.HOTBAR, GuiElement.HOTBAR);
        mapping.put(HudElement.HEALTH, GuiElement.HEALTH);
        mapping.put(HudElement.PROGRESS_BAR, GuiElement.PROGRESS_BAR);
        mapping.put(HudElement.FOOD_BAR, GuiElement.FOOD_BAR);
        mapping.put(HudElement.AIR_BUBBLES_BAR, GuiElement.AIR_BUBBLES_BAR);
        mapping.put(HudElement.VEHICLE_HEALTH, GuiElement.VEHICLE_HEALTH);
        mapping.put(HudElement.EFFECTS_BAR, GuiElement.EFFECTS_BAR);
        mapping.put(HudElement.ITEM_TEXT_POPUP, GuiElement.ITEM_TEXT_POPUP);
        return Map.copyOf(mapping);
    }
}
