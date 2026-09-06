package dev.rafo.bedrockbridge.transport

import dev.rafo.bedrockbridge.protocol.BridgeMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteSoundCatalogTest {
    @Test
    fun `só publica o catálogo depois de receber todas as partes`() {
        val catalog = RemoteSoundCatalog()
        catalog.begin(BridgeMessage.Welcome(7, 2, 2, true))

        assertFalse(catalog.accept(BridgeMessage.CatalogChunk(7, 1, 2, listOf("historia:final"))))
        assertNull(catalog.resolve("historia:intro"))

        assertTrue(catalog.accept(BridgeMessage.CatalogChunk(7, 0, 2, listOf("historia:intro"))))
        assertEquals("historia:intro", catalog.resolve("historia:intro"))
        assertEquals("historia:final", catalog.resolve("historia:final"))
    }

    @Test
    fun `ignora partes antigas depois de mudar a geração`() {
        val catalog = RemoteSoundCatalog()
        catalog.begin(BridgeMessage.Welcome(1, 1, 1, true))
        catalog.begin(BridgeMessage.Welcome(2, 1, 1, true))

        assertFalse(catalog.accept(BridgeMessage.CatalogChunk(1, 0, 1, listOf("antigo"))))
        assertTrue(catalog.accept(BridgeMessage.CatalogChunk(2, 0, 1, listOf("novo"))))
        assertEquals("novo", catalog.resolve("novo"))
        assertNull(catalog.resolve("antigo"))
    }
}
