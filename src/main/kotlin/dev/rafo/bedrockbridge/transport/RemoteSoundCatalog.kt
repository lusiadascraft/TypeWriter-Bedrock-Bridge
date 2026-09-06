package dev.rafo.bedrockbridge.transport

import dev.rafo.bedrockbridge.protocol.BridgeMessage
import dev.rafo.bedrockbridge.sound.BedrockSoundCatalog

internal class RemoteSoundCatalog {
    private val lock = Any()
    private var generation: Long? = null
    private var expectedChunks = 0
    private var expectedDefinitions = 0
    private val chunks = mutableMapOf<Int, List<String>>()

    @Volatile
    private var catalog = BedrockSoundCatalog.EMPTY

    @Volatile
    var synchronized: Boolean = false
        private set

    val size: Int
        get() = catalog.size

    fun begin(message: BridgeMessage.Welcome) = synchronized(lock) {
        require(message.totalChunks in 0..MAX_CHUNKS) { "Número de partes do catálogo inválido" }
        require(message.soundDefinitions in 0..MAX_DEFINITIONS) { "Número de sons inválido" }

        if (generation == message.catalogGeneration && synchronized) return
        if (generation == message.catalogGeneration && expectedChunks == message.totalChunks) return

        generation = message.catalogGeneration
        expectedChunks = message.totalChunks
        expectedDefinitions = message.soundDefinitions
        chunks.clear()
        catalog = BedrockSoundCatalog.EMPTY
        synchronized = message.totalChunks == 0 && message.soundDefinitions == 0
    }

    fun accept(message: BridgeMessage.CatalogChunk): Boolean = synchronized(lock) {
        if (generation != message.catalogGeneration || message.totalChunks != expectedChunks) return false
        if (message.index !in 0 until expectedChunks) return false

        chunks.putIfAbsent(message.index, message.definitions)
        if (chunks.size != expectedChunks) return false

        val definitions = (0 until expectedChunks).flatMap { chunks[it].orEmpty() }
        if (definitions.size != expectedDefinitions) {
            reset()
            return false
        }

        catalog = BedrockSoundCatalog.fromDefinitions(definitions)
        synchronized = true
        true
    }

    fun resolve(javaIdentifier: String): String? = catalog.resolve(javaIdentifier)

    private fun reset() {
        generation = null
        expectedChunks = 0
        expectedDefinitions = 0
        chunks.clear()
        catalog = BedrockSoundCatalog.EMPTY
        synchronized = false
    }

    private companion object {
        const val MAX_CHUNKS = 1_024
        const val MAX_DEFINITIONS = 100_000
    }
}
