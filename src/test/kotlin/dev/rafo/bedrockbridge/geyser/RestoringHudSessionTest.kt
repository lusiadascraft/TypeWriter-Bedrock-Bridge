package dev.rafo.bedrockbridge.geyser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestoringHudSessionTest {
    @Test
    fun `oculta todo o HUD ao abrir`() {
        val camera = FakeHudCamera()

        RestoringHudSession(camera)

        assertEquals(HudElement.entries.toSet(), camera.hiddenElements())
    }

    @Test
    fun `volta a ocultar elementos redefinidos durante a cinematic`() {
        val camera = FakeHudCamera()
        val session = RestoringHudSession(camera)
        camera.reset(setOf(HudElement.HOTBAR, HudElement.HEALTH))

        session.keepActive()

        assertTrue(HudElement.HOTBAR in camera.hiddenElements())
        assertTrue(HudElement.HEALTH in camera.hiddenElements())
    }

    @Test
    fun `restaura os elementos que estavam visíveis`() {
        val camera = FakeHudCamera()
        val session = RestoringHudSession(camera)

        session.close()

        assertTrue(camera.hiddenElements().isEmpty())
    }

    @Test
    fun `preserva os elementos que já estavam ocultos`() {
        val original = setOf(HudElement.HOTBAR, HudElement.CROSSHAIR)
        val camera = FakeHudCamera(original)
        val session = RestoringHudSession(camera)

        camera.reset(HudElement.entries.toSet())
        session.close()

        assertEquals(original, camera.hiddenElements())
    }

    @Test
    fun `fechar mais de uma vez não altera o estado restaurado`() {
        val camera = FakeHudCamera()
        val session = RestoringHudSession(camera)

        session.close()
        session.close()

        assertEquals(1, camera.resetCalls)
    }

    private class FakeHudCamera(
        initiallyHidden: Set<HudElement> = emptySet(),
    ) : HudCamera {
        private val hidden = initiallyHidden.toMutableSet()
        var resetCalls: Int = 0
            private set

        override fun hiddenElements(): Set<HudElement> = hidden.toSet()

        override fun hide(elements: Set<HudElement>) {
            hidden += elements
        }

        override fun reset(elements: Set<HudElement>) {
            resetCalls++
            hidden -= elements
        }
    }
}
