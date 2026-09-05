package dev.rafo.bedrockbridge.state

import java.util.UUID

internal fun interface CinematicSessionFactory {
    fun open(playerId: UUID): CinematicSession?
}

internal interface CinematicSession : AutoCloseable {
    fun keepActive()
}

internal class CinematicPlayerRegistry(
    private val onFailure: (operation: String, error: Throwable) -> Unit = { _, _ -> },
    private val sessionFactory: CinematicSessionFactory,
) {
    private data class ActiveSession(
        val pageId: String,
        val session: CinematicSession,
    )

    private val lock = Any()
    private val sessions = LinkedHashMap<UUID, ActiveSession>()

    val activeCount: Int
        get() = synchronized(lock) { sessions.size }

    fun isActive(playerId: UUID): Boolean = synchronized(lock) {
        playerId in sessions
    }

    fun start(playerId: UUID, pageId: String): Boolean = synchronized(lock) {
        val current = sessions[playerId]
        if (current?.pageId == pageId) return false

        if (current != null) {
            sessions.remove(playerId)
            close(playerId, current.session)
        }

        val session = runCatching { sessionFactory.open(playerId) }
            .onFailure { onFailure("iniciar a cinematic de $playerId", it) }
            .getOrNull()
            ?: return false

        sessions[playerId] = ActiveSession(pageId, session)
        true
    }

    fun tick(playerId: UUID) = synchronized(lock) {
        val session = sessions[playerId]?.session ?: return
        runCatching(session::keepActive)
            .onFailure { onFailure("manter o HUD oculto para $playerId", it) }
        Unit
    }

    fun finish(playerId: UUID, pageId: String): Boolean = synchronized(lock) {
        val current = sessions[playerId] ?: return false
        if (current.pageId != pageId) return false

        sessions.remove(playerId)
        close(playerId, current.session)
        true
    }

    fun remove(playerId: UUID): Boolean = synchronized(lock) {
        val current = sessions.remove(playerId) ?: return false
        close(playerId, current.session)
        true
    }

    fun clear(): Int = synchronized(lock) {
        val activeSessions = sessions.toMap()
        sessions.clear()
        activeSessions.forEach { (playerId, active) ->
            close(playerId, active.session)
        }
        activeSessions.size
    }

    private fun close(playerId: UUID, session: CinematicSession) {
        runCatching(session::close)
            .onFailure { onFailure("restaurar o HUD de $playerId", it) }
    }
}
