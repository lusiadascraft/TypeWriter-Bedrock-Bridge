package dev.rafo.bedrockbridge.velocity.sound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BedrockSoundCatalog {
    private static final String SOUND_DEFINITIONS_FILE = "sound_definitions.json";
    private static final String SOUND_DEFINITIONS_PATH = "sounds/sound_definitions.json";

    private final Map<String, String> definitions;

    private BedrockSoundCatalog(Set<String> definitions) {
        this.definitions = new LinkedHashMap<>();
        for (String definition : definitions) {
            this.definitions.putIfAbsent(normalize(definition), definition);
        }
    }

    public static BedrockSoundCatalog load(Path packDirectory, BiConsumer<String, Throwable> onFailure) {
        if (packDirectory == null || !Files.isDirectory(packDirectory)) {
            return new BedrockSoundCatalog(Set.of());
        }

        Set<String> definitions = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(packDirectory)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (name.equalsIgnoreCase(SOUND_DEFINITIONS_FILE)) {
                    readFile(path, definitions, onFailure);
                    return;
                }

                String lowerName = name.toLowerCase(Locale.ROOT);
                if (lowerName.endsWith(".zip") || lowerName.endsWith(".mcpack")) {
                    readArchive(path, definitions, onFailure);
                }
            });
        } catch (Throwable error) {
            onFailure.accept(packDirectory.toString(), error);
        }
        return new BedrockSoundCatalog(definitions);
    }

    public int size() {
        return definitions.size();
    }

    public Set<String> definitions() {
        return Set.copyOf(definitions.values());
    }

    public String resolve(String javaIdentifier) {
        String identifier = javaIdentifier.trim();
        if (identifier.isEmpty()) {
            return null;
        }

        for (String candidate : candidates(identifier)) {
            String definition = definitions.get(normalize(candidate));
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    private static void readFile(
            Path path,
            Set<String> definitions,
            BiConsumer<String, Throwable> onFailure
    ) {
        try (Reader reader = Files.newBufferedReader(path)) {
            definitions.addAll(readDefinitions(reader));
        } catch (Throwable error) {
            onFailure.accept(path.toString(), error);
        }
    }

    private static void readArchive(
            Path path,
            Set<String> definitions,
            BiConsumer<String, Throwable> onFailure
    ) {
        try (ZipFile archive = new ZipFile(path.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !normalizePath(entry.getName()).endsWith(SOUND_DEFINITIONS_PATH)) {
                    continue;
                }
                try (Reader reader = new java.io.InputStreamReader(
                        archive.getInputStream(entry),
                        java.nio.charset.StandardCharsets.UTF_8
                )) {
                    definitions.addAll(readDefinitions(reader));
                }
            }
        } catch (Throwable error) {
            onFailure.accept(path.toString(), error);
        }
    }

    private static Set<String> readDefinitions(Reader reader) throws IOException {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject sounds = root.getAsJsonObject("sound_definitions");
        return sounds == null ? Set.of() : sounds.keySet();
    }

    private static List<String> candidates(String identifier) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(identifier);

        int separator = identifier.indexOf(':');
        if (separator >= 0) {
            String namespace = identifier.substring(0, separator);
            String path = identifier.substring(separator + 1);
            if (namespace.equals("minecraft")) {
                candidates.add(path);
            }
            candidates.add(namespace + "." + path);
            candidates.add(namespace + "/" + path);
        }
        return List.copyOf(candidates);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String value) {
        return value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
