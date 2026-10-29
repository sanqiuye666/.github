package cn.dreamingfish.launcher;

import java.util.Map;

/** Capability metadata only; it does not advertise a working launcher service. */
public record LauncherModule(int apiVersion, Map<String, CapabilityState> capabilities) {
    public enum CapabilityState { AVAILABLE, PENDING, UNCONFIGURED, UNAVAILABLE }

    public LauncherModule {
        if (apiVersion < 1 || capabilities == null) throw new IllegalArgumentException("Invalid module metadata");
        capabilities = Map.copyOf(capabilities);
    }

    public static LauncherModule framework() {
        return new LauncherModule(1, Map.of(
                "versions", CapabilityState.PENDING, "mods", CapabilityState.PENDING,
                "instances", CapabilityState.PENDING, "accounts", CapabilityState.PENDING,
                "launch", CapabilityState.PENDING, "content", CapabilityState.PENDING,
                "scheduling", CapabilityState.PENDING));
    }
}
