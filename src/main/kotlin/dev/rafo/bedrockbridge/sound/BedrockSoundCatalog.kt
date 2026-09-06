package dev.rafo.bedrockbridge.sound

import com.google.gson.JsonParser
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

internal class BedrockSoundCatalog private constructor(
    definitions: Set<String>,
) {
    private val definitionsByNormalizedName = definitions.associateBy { it.normalized() }

    val definitions: Set<String>
        get() = definitionsByNormalizedName.values.toSet()

    val size: Int
        get() = definitionsByNormalizedName.size

    fun resolve(javaIdentifier: String): String? {
        val identifier = javaIdentifier.trim()
        if (identifier.isEmpty()) return null

        return candidates(identifier)
            .firstNotNullOfOrNull { definitionsByNormalizedName[it.normalized()] }
    }

    companion object {
        val EMPTY = BedrockSoundCatalog(emptySet())

        fun fromDefinitions(definitions: Iterable<String>): BedrockSoundCatalog =
            BedrockSoundCatalog(definitions.filter(String::isNotBlank).toSet())

        fun load(
            packDirectory: Path?,
            onFailure: (source: String, error: Throwable) -> Unit = { _, _ -> },
        ): BedrockSoundCatalog {
            if (packDirectory == null || !Files.isDirectory(packDirectory)) return EMPTY

            val definitions = linkedSetOf<String>()
            runCatching {
                Files.walk(packDirectory).use { paths ->
                    paths.filter(Path::isRegularFile).forEach { path ->
                        when {
                            path.name.equals(SOUND_DEFINITIONS_FILE, ignoreCase = true) -> {
                                readFile(path, definitions, onFailure)
                            }

                            path.extension.equals("zip", ignoreCase = true) ||
                                path.extension.equals("mcpack", ignoreCase = true) -> {
                                readArchive(path, definitions, onFailure)
                            }
                        }
                    }
                }
            }.onFailure { onFailure(packDirectory.toString(), it) }

            return fromDefinitions(definitions)
        }

        private fun readFile(
            path: Path,
            definitions: MutableSet<String>,
            onFailure: (String, Throwable) -> Unit,
        ) {
            runCatching {
                Files.newBufferedReader(path).use { reader -> definitions += reader.soundDefinitions() }
            }.onFailure { onFailure(path.toString(), it) }
        }

        private fun readArchive(
            path: Path,
            definitions: MutableSet<String>,
            onFailure: (String, Throwable) -> Unit,
        ) {
            runCatching {
                ZipFile(path.toFile()).use { archive ->
                    archive.entries().asSequence()
                        .filterNot { it.isDirectory }
                        .filter { it.name.normalizedPath().endsWith(SOUND_DEFINITIONS_PATH) }
                        .forEach { entry ->
                            archive.getInputStream(entry).bufferedReader().use { reader ->
                                definitions += reader.soundDefinitions()
                            }
                        }
                }
            }.onFailure { onFailure(path.toString(), it) }
        }

        private fun Reader.soundDefinitions(): Set<String> {
            val root = JsonParser.parseReader(this).asJsonObject
            val definitions = root.getAsJsonObject("sound_definitions") ?: return emptySet()
            return definitions.keySet()
        }

        private fun candidates(identifier: String): Sequence<String> = sequence {
            yield(identifier)

            val separator = identifier.indexOf(':')
            if (separator < 0) return@sequence

            val namespace = identifier.substring(0, separator)
            val path = identifier.substring(separator + 1)
            if (namespace == MINECRAFT_NAMESPACE) yield(path)
            yield("$namespace.$path")
            yield("$namespace/$path")
        }.distinct()

        private fun String.normalized(): String = trim().lowercase(Locale.ROOT)

        private fun String.normalizedPath(): String = replace('\\', '/').lowercase(Locale.ROOT)

        private const val MINECRAFT_NAMESPACE = "minecraft"
        private const val SOUND_DEFINITIONS_FILE = "sound_definitions.json"
        private const val SOUND_DEFINITIONS_PATH = "sounds/sound_definitions.json"
    }
}
