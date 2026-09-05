package dev.rafo.bedrockbridge.geyser

import dev.rafo.bedrockbridge.state.CinematicSession
import java.util.concurrent.atomic.AtomicBoolean

internal enum class HudElement {
    PAPER_DOLL,
    ARMOR,
    TOOL_TIPS,
    TOUCH_CONTROLS,
    CROSSHAIR,
    HOTBAR,
    HEALTH,
    PROGRESS_BAR,
    FOOD_BAR,
    AIR_BUBBLES_BAR,
    VEHICLE_HEALTH,
    EFFECTS_BAR,
    ITEM_TEXT_POPUP,
}

internal interface HudCamera {
    fun hiddenElements(): Set<HudElement>

    fun hide(elements: Set<HudElement>)

    fun reset(elements: Set<HudElement>)
}

internal class RestoringHudSession(
    private val camera: HudCamera,
) : CinematicSession {
    private val closed = AtomicBoolean(false)
    private val originallyHidden = camera.hiddenElements().intersect(CINEMATIC_HUD_ELEMENTS)

    init {
        keepActive()
    }

    override fun keepActive() {
        if (closed.get()) return
        val missing = CINEMATIC_HUD_ELEMENTS - camera.hiddenElements()
        if (missing.isNotEmpty()) camera.hide(missing)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val addedByBridge = CINEMATIC_HUD_ELEMENTS - originallyHidden
        try {
            if (addedByBridge.isNotEmpty()) camera.reset(addedByBridge)
        } finally {
            val hiddenBeforeCinematic = originallyHidden - camera.hiddenElements()
            if (hiddenBeforeCinematic.isNotEmpty()) camera.hide(hiddenBeforeCinematic)
        }
    }

    private companion object {
        val CINEMATIC_HUD_ELEMENTS = HudElement.entries.toSet()
    }
}
