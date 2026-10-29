package cn.dreamingfish.dispatch;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DispatchModuleTest {
    @Test void frameworkDoesNotClaimToAuthenticateOrManagePlayers() {
        var module = DispatchModule.framework();
        assertEquals(1, module.apiVersion());
        assertEquals(Set.of("authentication", "groups", "delivery", "telemetry", "account-control"), module.capabilities().keySet());
        assertTrue(module.capabilities().values().stream().allMatch(s -> s == DispatchModule.CapabilityState.PENDING));
        assertThrows(UnsupportedOperationException.class, () -> module.capabilities().clear());
        assertTrue(DispatchDirectory.class.isInterface());
    }

    @Test void panelIdentityRequiresAnExplicitGroupRoleAndState() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new PanelIdentity(id, "player", PanelIdentity.Role.PLAYER, " ", PanelIdentity.Status.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> new PanelIdentity(id, "player", null, "default", PanelIdentity.Status.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> new PanelIdentity(id, "player", PanelIdentity.Role.PLAYER, "default", null));
        assertThrows(IllegalArgumentException.class, () -> new PanelIdentity(null, "player", PanelIdentity.Role.PLAYER, "default", PanelIdentity.Status.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> new PanelIdentity(id, "player\nadmin", PanelIdentity.Role.PLAYER, "default", PanelIdentity.Status.ACTIVE));
        assertEquals(Set.of("id", "username", "role", "groupId", "status"), java.util.Arrays.stream(PanelIdentity.class.getRecordComponents()).map(c -> c.getName()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void aGroupIsMetadataRatherThanALocalGrantOfPermissions() {
        assertEquals("default", new PlayerGroup("default", "默认组", true).id());
        assertThrows(IllegalArgumentException.class, () -> new PlayerGroup("", "name", false));
        assertThrows(IllegalArgumentException.class, () -> new PlayerGroup("id", " ", false));
    }

    @Test void telemetryAllowsUnknownVersionsAndRejectsInvalidDurations() {
        var id = UUID.randomUUID();
        var telemetry = new PlayerTelemetry(id, null, null, Duration.ZERO, Instant.EPOCH);
        assertNull(telemetry.gameVersion());
        assertNull(telemetry.packVersion());
        assertThrows(IllegalArgumentException.class, () -> new PlayerTelemetry(id, "1.20.1", "r1", Duration.ofSeconds(-1), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new PlayerTelemetry(id, null, null, null, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new PlayerTelemetry(id, null, null, Duration.ZERO, null));
    }
}
