package cn.dreamingfish.dispatch;

import java.util.Map;

/** Dispatch capabilities, not an authentication or administration implementation. */
public record DispatchModule(int apiVersion, Map<String, CapabilityState> capabilities) {
    public enum CapabilityState { AVAILABLE, PENDING, UNCONFIGURED, UNAVAILABLE }

    public DispatchModule {
        if (apiVersion < 1 || capabilities == null) throw new IllegalArgumentException("Invalid module metadata");
        capabilities = Map.copyOf(capabilities);
    }

    public static DispatchModule framework() {
        return new DispatchModule(1, Map.of("authentication", CapabilityState.PENDING,
                "groups", CapabilityState.PENDING, "delivery", CapabilityState.PENDING,
                "telemetry", CapabilityState.PENDING, "account-control", CapabilityState.PENDING));
    }
}
