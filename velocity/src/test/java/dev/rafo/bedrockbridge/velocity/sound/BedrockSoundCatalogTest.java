package dev.rafo.bedrockbridge.velocity.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BedrockSoundCatalogTest {
    @TempDir
    Path packs;

    @Test
    void loadsDirectoriesAndArchives() throws Exception {
        Path sounds = Files.createDirectories(packs.resolve("diretorio/sounds"));
        Files.writeString(sounds.resolve("sound_definitions.json"), definitions("lusiadascraft:intro"));

        try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(packs.resolve("outro.mcpack")))) {
            archive.putNextEntry(new ZipEntry("sounds/sound_definitions.json"));
            archive.write(definitions("historia.final").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            archive.closeEntry();
        }

        BedrockSoundCatalog catalog = BedrockSoundCatalog.load(packs, (source, error) -> {});

        assertEquals(2, catalog.size());
        assertEquals("lusiadascraft:intro", catalog.resolve("lusiadascraft:intro"));
        assertEquals("historia.final", catalog.resolve("historia:final"));
    }

    @Test
    void reportsInvalidArchivesWithoutLosingValidSounds() throws Exception {
        Files.writeString(packs.resolve("invalido.mcpack"), "não é um zip");
        Path sounds = Files.createDirectories(packs.resolve("valido/sounds"));
        Files.writeString(sounds.resolve("sound_definitions.json"), definitions("som:valido"));
        List<String> failures = new ArrayList<>();

        BedrockSoundCatalog catalog = BedrockSoundCatalog.load(packs, (source, error) -> failures.add(source));

        assertEquals("som:valido", catalog.resolve("som:valido"));
        assertNull(catalog.resolve("som:ausente"));
        assertTrue(failures.getFirst().endsWith("invalido.mcpack"));
    }

    private static String definitions(String identifier) {
        return "{\"format_version\":\"1.14.0\",\"sound_definitions\":{\""
                + identifier
                + "\":{\"sounds\":[\""
                + identifier
                + "\"]}}}";
    }
}
