package dev.rafo.bedrockbridge.state

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CinematicPlayerRegistryTest {
    @Test
    fun `fecha a sessão no fim da cinematic`() {
        val session = FakeSession()
        val registry = CinematicPlayerRegistry { session }
        val playerId = UUID.randomUUID()

        assertTrue(registry.start(playerId, "intro"))
        assertTrue(registry.finish(playerId, "intro"))

        assertEquals(1, session.closeCalls)
        assertFalse(registry.isActive(playerId))
    }

    @Test
    fun `não abre duas sessões para a mesma cinematic`() {
        var openCalls = 0
        val registry = CinematicPlayerRegistry {
            openCalls++
            FakeSession()
        }
        val playerId = UUID.randomUUID()

        assertTrue(registry.start(playerId, "intro"))
        assertFalse(registry.start(playerId, "intro"))

        assertEquals(1, openCalls)
        assertEquals(1, registry.activeCount)
    }

    @Test
    fun `uma nova cinematic substitui a anterior e ignora o fim antigo`() {
        val sessions = mutableListOf<FakeSession>()
        val registry = CinematicPlayerRegistry {
            FakeSession().also(sessions::add)
        }
        val playerId = UUID.randomUUID()

        registry.start(playerId, "intro")
        registry.start(playerId, "final")

        assertEquals(1, sessions.first().closeCalls)
        assertFalse(registry.finish(playerId, "intro"))
        assertTrue(registry.isActive(playerId))
        assertTrue(registry.finish(playerId, "final"))
        assertEquals(1, sessions.last().closeCalls)
    }

    @Test
    fun `remove e restaura o estado quando o jogador sai`() {
        val session = FakeSession()
        val registry = CinematicPlayerRegistry { session }
        val playerId = UUID.randomUUID()

        registry.start(playerId, "intro")

        assertTrue(registry.remove(playerId))
        assertEquals(1, session.closeCalls)
        assertEquals(0, registry.activeCount)
    }

    @Test
    fun `limpa todas as sessões no shutdown`() {
        val sessions = mutableListOf<FakeSession>()
        val registry = CinematicPlayerRegistry {
            FakeSession().also(sessions::add)
        }

        repeat(3) { registry.start(UUID.randomUUID(), "pagina-$it") }

        assertEquals(3, registry.clear())
        assertEquals(0, registry.activeCount)
        assertTrue(sessions.all { it.closeCalls == 1 })
    }

    @Test
    fun `mantém o registo utilizável quando uma sessão falha`() {
        val errors = mutableListOf<String>()
        val registry = CinematicPlayerRegistry(
            sessionFactory = { FakeSession(failOnKeepActive = true, failOnClose = true) },
            onFailure = { operation, _ -> errors += operation },
        )
        val playerId = UUID.randomUUID()

        registry.start(playerId, "intro")
        registry.tick(playerId)
        registry.remove(playerId)

        assertEquals(2, errors.size)
        assertEquals(0, registry.activeCount)
    }

    @Test
    fun `não acompanha jogadores sem sessão Bedrock`() {
        val registry = CinematicPlayerRegistry { null }
        val playerId = UUID.randomUUID()

        assertFalse(registry.start(playerId, "intro"))
        assertFalse(registry.isActive(playerId))
    }

    private class FakeSession(
        private val failOnKeepActive: Boolean = false,
        private val failOnClose: Boolean = false,
    ) : CinematicSession {
        var keepActiveCalls = 0
            private set
        var closeCalls = 0
            private set

        override fun keepActive() {
            keepActiveCalls++
            if (failOnKeepActive) error("falha ao manter")
        }

        override fun close() {
            closeCalls++
            if (failOnClose) error("falha ao fechar")
        }
    }
}
