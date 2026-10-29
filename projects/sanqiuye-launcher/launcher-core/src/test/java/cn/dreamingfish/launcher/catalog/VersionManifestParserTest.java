package cn.dreamingfish.launcher.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class VersionManifestParserTest {
    private final VersionManifestParser parser = new VersionManifestParser();

    @Test void parsesAllTypesAndFiltersWithoutHardcodedVersionLists() {
        var manifest = parser.parse(ManifestFixtures.bytes());
        assertEquals(4, manifest.versions().size());
        assertEquals("1.20.1", manifest.latestRelease());
        assertEquals(1, manifest.search(" 1.20 ", OfficialVersion.Type.RELEASE).size());
        assertEquals(1, manifest.search("24W", null).size());
        assertEquals(0, manifest.search("missing", null).size());
        assertThrows(UnsupportedOperationException.class, () -> manifest.versions().clear());
    }

    @ParameterizedTest @ValueSource(strings = {
            "http://piston-meta.mojang.com/v1/version.json", "https://evil.example/version.json",
            "https://user@piston-meta.mojang.com/version.json", "https://piston-meta.mojang.com:444/version.json",
            "https://piston-meta.mojang.com/version.json#bad"})
    void rejectsUnsafeMetadataAddresses(String url) {
        String json = ManifestFixtures.json().replace("https://piston-meta.mojang.com/v1/packages/" + "a".repeat(40) + "/version.json", url);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsDuplicatesMissingLatestAndInvalidFields() {
        for (String invalid : new String[]{
                ManifestFixtures.json().replace("\"b1.0\"", "\"a1.0\""),
                ManifestFixtures.json().replace("\"release\":\"1.20.1\"", "\"release\":\"missing\""),
                ManifestFixtures.json().replace("\"sha1\":\"" + "a".repeat(40), "\"sha1\":\"bad"),
                ManifestFixtures.json().replace("2024-01-01T00:00:00+00:00", "yesterday"),
                ManifestFixtures.json().replace("\"id\":\"a1.0\"", "\"id\":\"" + "x".repeat(129) + "\""),
                ManifestFixtures.json().replace("old_beta", "unknown"),
                ManifestFixtures.json().replace("\"latest\":", "\"latest\":{},\"latest\":"),
                ManifestFixtures.json() + "{}", "null", "[]"}) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(invalid.getBytes(StandardCharsets.UTF_8)), invalid);
        }
    }

    @Test void rejectsOversizedDocumentsAndInvalidDirectModelConstruction() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[4 * 1024 * 1024 + 1]));
        assertThrows(IllegalArgumentException.class, () -> new VersionManifest("missing", "missing", java.util.List.of()));
    }
}
