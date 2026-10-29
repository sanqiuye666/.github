package cn.dreamingfish.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LauncherModuleTest {
    @TempDir Path root;

    @Test void frameworkDoesNotAdvertiseUnimplementedServices() {
        var module = LauncherModule.framework();
        assertEquals(1, module.apiVersion());
        assertEquals(Set.of("versions", "mods", "instances", "accounts", "launch", "content", "scheduling"), module.capabilities().keySet());
        assertTrue(module.capabilities().values().stream().allMatch(s -> s == LauncherModule.CapabilityState.PENDING));
        assertThrows(UnsupportedOperationException.class, () -> module.capabilities().clear());
    }

    @Test void instanceNormalizesAnAbsoluteDirectoryWithoutCreatingIt() {
        var destination = root.resolve("not-created/../isolated");
        var instance = new GameInstance("survival", "生存", "1.20.1", GameInstance.LoaderKind.FABRIC, destination);
        assertEquals(root.resolve("isolated"), instance.directory());
        assertFalse(Files.exists(instance.directory()));
    }

    @Test void instanceRejectsIncompleteOrRelativeMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GameInstance(" ", "name", "1.20.1", GameInstance.LoaderKind.VANILLA, root));
        assertThrows(IllegalArgumentException.class, () -> new GameInstance("id", "", "1.20.1", GameInstance.LoaderKind.VANILLA, root));
        assertThrows(IllegalArgumentException.class, () -> new GameInstance("id", "name", null, GameInstance.LoaderKind.VANILLA, root));
        assertThrows(IllegalArgumentException.class, () -> new GameInstance("id", "name", "1.20.1", null, root));
        assertThrows(IllegalArgumentException.class, () -> new GameInstance("id", "name", "1.20.1", GameInstance.LoaderKind.VANILLA, Path.of("relative")));
    }

    @Test void identityDoesNotImplyPanelAuthenticationOrHoldCredentials() {
        var identity = new GameIdentity(UUID.randomUUID(), "Steve", GameIdentity.Kind.OFFLINE);
        assertEquals(GameIdentity.Kind.OFFLINE, identity.kind());
        assertEquals(Set.of("id", "name", "kind"), java.util.Arrays.stream(GameIdentity.class.getRecordComponents()).map(c -> c.getName()).collect(java.util.stream.Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> new GameIdentity(null, "Steve", GameIdentity.Kind.OFFLINE));
        assertThrows(IllegalArgumentException.class, () -> new GameIdentity(identity.id(), " ", GameIdentity.Kind.OFFLINE));
        assertThrows(IllegalArgumentException.class, () -> new GameIdentity(identity.id(), "Steve\nadmin", GameIdentity.Kind.OFFLINE));
        assertThrows(IllegalArgumentException.class, () -> new GameIdentity(identity.id(), "Steve", null));
    }

    @Test void loaderKindsCoverThePlannedAdaptersWithoutClaimingCompatibility() {
        assertEquals(Set.of("VANILLA", "FORGE", "FABRIC", "NEOFORGE", "QUILT", "OPTIFINE"), java.util.Arrays.stream(GameInstance.LoaderKind.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertTrue(InstanceCatalog.class.isInterface());
    }
}
