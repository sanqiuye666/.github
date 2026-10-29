package cn.dreamingfish.launcher.launch;

import cn.dreamingfish.launcher.vanilla.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** 只解析官方参数边界；固定值替换一次，三个运行路径留到准备完成后绑定。 */
public final class LaunchArguments {
    public enum Slot {
        NATIVES_DIRECTORY("natives_directory"), ASSETS_ROOT("assets_root"), GAME_ASSETS("game_assets");
        private final String name;
        Slot(String name) { this.name = name; }
    }
    private record Part(String text, Slot slot) { }
    public static final class Argument {
        private final List<Part> parts;
        private Argument(List<Part> parts) { this.parts = List.copyOf(parts); }
        public String resolve(Map<Slot, String> paths) throws LaunchFailure {
            var result = new StringBuilder();
            for (Part part : parts) {
                String value = part.slot == null ? part.text : paths.get(part.slot);
                if (value == null) throw unsupported();
                result.append(value);
            }
            return result.toString();
        }
        public String template() {
            var result = new StringBuilder();
            for (Part part : parts) result.append(part.slot == null ? part.text : "${" + part.slot.name + "}");
            return result.toString();
        }
    }
    private LaunchArguments() { }
    public static Argument literal(String value) { return new Argument(List.of(new Part(value, null))); }
    public static Argument compile(String template, Map<String, String> fixed) throws LaunchFailure {
        if (template == null || template.length() > 32768 || template.chars().anyMatch(Character::isISOControl)) throw unsupported();
        var parts = new ArrayList<Part>(); int cursor = 0, start;
        while ((start = template.indexOf("${", cursor)) >= 0) {
            parts.add(new Part(template.substring(cursor, start), null));
            int end = template.indexOf('}', start + 2);
            if (end < 0) throw unsupported();
            String name = template.substring(start + 2, end);
            Slot slot = Arrays.stream(Slot.values()).filter(value -> value.name.equals(name)).findFirst().orElse(null);
            if (slot != null) parts.add(new Part(null, slot));
            else if (fixed.containsKey(name)) parts.add(new Part(fixed.get(name), null));
            else throw unsupported();
            cursor = end + 1;
        }
        parts.add(new Part(template.substring(cursor), null));
        return new Argument(parts);
    }
    public static List<String> splitLegacy(String source) throws LaunchFailure {
        if (source == null || source.length() > 32768) throw unsupported();
        var result = new ArrayList<String>(); var current = new StringBuilder();
        char quote = 0; boolean present = false;
        for (int i = 0; i < source.length(); i++) {
            char value = source.charAt(i);
            if (Character.isISOControl(value)) throw unsupported();
            if (value == '\\' && i + 1 < source.length() && source.charAt(i + 1) == quote && quote != 0) {
                current.append(source.charAt(++i)); present = true;
            } else if (value == '\'' || value == '"') {
                if (quote == 0) { quote = value; present = true; }
                else if (quote == value) quote = 0;
                else current.append(value);
            } else if (Character.isWhitespace(value) && quote == 0) {
                if (present) { result.add(current.toString()); current.setLength(0); present = false; }
            } else { current.append(value); present = true; }
        }
        if (quote != 0) throw unsupported();
        if (present) result.add(current.toString());
        if (result.size() > 1024) throw unsupported();
        return List.copyOf(result);
    }
    public static List<Argument> read(JsonNode values, Platform platform, Map<String, Boolean> features, Map<String, String> fixed) throws LaunchFailure {
        if (values == null || !values.isArray() || values.size() > 1024) throw unsupported();
        var result = new ArrayList<Argument>();
        for (JsonNode value : values) {
            if (value.isTextual()) result.add(compile(value.textValue(), fixed));
            else if (value.isObject()) {
                if (unknown(value, Set.of("rules", "value")) || !value.has("value")) throw unsupported();
                if (!allowed(value.get("rules"), platform, features)) continue;
                JsonNode content = value.get("value");
                if (content.isTextual()) result.add(compile(content.textValue(), fixed));
                else if (content.isArray()) {
                    for (JsonNode item : content) { if (!item.isTextual()) throw unsupported(); result.add(compile(item.textValue(), fixed)); }
                } else throw unsupported();
            } else throw unsupported();
            if (result.size() > 1024) throw unsupported();
        }
        return List.copyOf(result);
    }
    public static boolean allowed(JsonNode rules, Platform platform, Map<String, Boolean> features) throws LaunchFailure {
        if (rules == null || rules.isMissingNode()) return true;
        if (!rules.isArray()) throw unsupported();
        for (JsonNode rule : rules) {
            if (!rule.isObject() || unknown(rule, Set.of("action", "os", "features"))) throw unsupported();
            if (rule.has("os") && (!rule.path("os").isObject() || unknown(rule.path("os"), Set.of("name", "version", "arch")))) throw unsupported();
            if (rule.path("os").has("name") && !rule.path("os").path("name").isTextual()) throw unsupported();
            if (rule.has("features")) {
                JsonNode flags = rule.path("features"); if (!flags.isObject()) throw unsupported();
                var keys = flags.fieldNames();
                while (keys.hasNext()) { String key = keys.next(); if (!features.containsKey(key) || !flags.get(key).isBoolean()) throw unsupported(); }
            }
        }
        try { return LibraryRules.allowed(rules, platform, features); }
        catch (IllegalArgumentException invalid) { throw unsupported(); }
    }
    private static boolean unknown(JsonNode node, Set<String> keys) {
        var names = node.fieldNames(); while (names.hasNext()) if (!keys.contains(names.next())) return true;
        return false;
    }
    static LaunchFailure unsupported() { return new LaunchFailure("UNSUPPORTED_LAUNCH_FORMAT", "暂不支持此官方启动参数格式，请保留当前安装并选择其他版本"); }
}
