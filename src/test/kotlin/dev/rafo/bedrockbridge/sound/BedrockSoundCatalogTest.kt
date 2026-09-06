package dev.rafo.bedrockbridge.sound

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BedrockSoundCatalogTest {
    @Test
    fun `lê definições de um pack em diretório`() {
        val packs = createTempDirectory("bedrockbridge-packs")
        val sounds = packs.resolve("Scaffolding/sounds").createDirectories()
        sounds.resolve("sound_definitions.json").writeText(definitions("lusiadascraft:intro"))

        val catalog = BedrockSoundCatalog.load(packs)

        assertEquals("lusiadascraft:intro", catalog.resolve("lusiadascraft:intro"))
        assertEquals(1, catalog.size)
    }

    @Test
    fun `lê definições de um ficheiro mcpack`() {
        val packs = createTempDirectory("bedrockbridge-packs")
        val pack = packs.resolve("scaffolding.mcpack")
        ZipOutputStream(pack.outputStream()).use { archive ->
            archive.putNextEntry(ZipEntry("sounds/sound_definitions.json"))
            archive.write(definitions("story.dialogue.welcome").toByteArray())
            archive.closeEntry()
        }

        val catalog = BedrockSoundCatalog.load(packs)

        assertEquals("story.dialogue.welcome", catalog.resolve("story:dialogue.welcome"))
    }

    @Test
    fun `aceita uma definição sem o namespace minecraft`() {
        val catalog = catalogWith("entity.player.levelup")

        assertEquals("entity.player.levelup", catalog.resolve("minecraft:entity.player.levelup"))
    }

    @Test
    fun `não substitui sons ausentes do pack Bedrock`() {
        val catalog = catalogWith("lusiadascraft:intro")

        assertNull(catalog.resolve("minecraft:block.note_block.pling"))
    }

    @Test
    fun `ignora packs inválidos sem perder os restantes`() {
        val packs = createTempDirectory("bedrockbridge-packs")
        Files.writeString(packs.resolve("invalido.mcpack"), "não é um zip")
        val sounds = packs.resolve("valido/sounds").createDirectories()
        sounds.resolve("sound_definitions.json").writeText(definitions("lusiadascraft:ok"))
        val failures = mutableListOf<String>()

        val catalog = BedrockSoundCatalog.load(packs) { source, _ -> failures += source }

        assertEquals("lusiadascraft:ok", catalog.resolve("lusiadascraft:ok"))
        assertTrue(failures.single().endsWith("invalido.mcpack"))
    }

    @Test
    fun `reconstrói um catálogo sincronizado pelo proxy`() {
        val catalog = BedrockSoundCatalog.fromDefinitions(
            listOf("lusiadascraft:intro", "minecraft:entity.player.levelup"),
        )

        assertEquals(2, catalog.size)
        assertEquals("lusiadascraft:intro", catalog.resolve("lusiadascraft:intro"))
        assertEquals("minecraft:entity.player.levelup", catalog.resolve("minecraft:entity.player.levelup"))
    }

    private fun catalogWith(vararg sounds: String): BedrockSoundCatalog {
        val packs = createTempDirectory("bedrockbridge-packs")
        val directory = packs.resolve("pack/sounds").createDirectories()
        directory.resolve("sound_definitions.json").writeText(definitions(*sounds))
        return BedrockSoundCatalog.load(packs)
    }

    private fun definitions(vararg sounds: String): String {
        val entries = sounds.joinToString(",") { "\"$it\": {\"sounds\": [\"$it\"]}" }
        return """{"format_version":"1.14.0","sound_definitions":{$entries}}"""
    }
}
