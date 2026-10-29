package cn.dreamingfish.launcher.instances;

import java.util.List;

public record InstanceListing(List<PreparedInstance> instances, List<Problem> problems) {
    public record Problem(String directoryId, String reason) {}
    public InstanceListing { instances = List.copyOf(instances); problems = List.copyOf(problems); }
}
