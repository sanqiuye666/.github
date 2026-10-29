package cn.dreamingfish.launcher.options;

import java.util.*;

/** 玩家单位与已验证游戏编码的唯一对照；不接受任意options键。 */
public enum GameOption {
    FOV("fov", "视场角", "画面", 70, 110, 70),
    MOUSE_SENSITIVITY("mouseSensitivity", "鼠标灵敏度（%）", "控制", 0, 200, 100),
    RENDER_DISTANCE("renderDistance", "视距（区块）", "画面", 2, 32, 12),
    MAX_FPS("maxFps", "最大帧率（260为无限制）", "画面", 10, 260, 120),
    GUI_SCALE("guiScale", "界面比例（0为自动）", "画面", 0, 6, 0),
    MASTER_VOLUME("soundCategory_master", "主音量（%）", "声音", 0, 100, 100),
    AUTO_JUMP("autoJump", "自动跳跃", "控制", false),
    BOB_VIEW("bobView", "视角摇晃", "画面", true),
    SUBTITLES("showSubtitles", "显示字幕", "声音", false),
    CHAT_COLORS("chatColors", "聊天颜色", "聊天", true),
    MAIN_HAND("mainHand", "主手", "皮肤部位", "right"),
    CAPE("modelPart_cape", "披风", "皮肤部位", true),
    JACKET("modelPart_jacket", "上衣", "皮肤部位", true),
    LEFT_SLEEVE("modelPart_left_sleeve", "左袖", "皮肤部位", true),
    RIGHT_SLEEVE("modelPart_right_sleeve", "右袖", "皮肤部位", true),
    LEFT_LEG("modelPart_left_pants_leg", "左裤腿", "皮肤部位", true),
    RIGHT_LEG("modelPart_right_pants_leg", "右裤腿", "皮肤部位", true),
    HAT("modelPart_hat", "帽子", "皮肤部位", true);

    private final String key, label, category, type;
    private final int min, max;
    private final Object defaultValue;
    GameOption(String key, String label, String category, int min, int max, int value) { this.key=key;this.label=label;this.category=category;type="integer";this.min=min;this.max=max;defaultValue=value; }
    GameOption(String key, String label, String category, boolean value) { this.key=key;this.label=label;this.category=category;type="boolean";min=0;max=0;defaultValue=value; }
    GameOption(String key, String label, String category, String value) { this.key=key;this.label=label;this.category=category;type="choice";min=0;max=0;defaultValue=value; }
    public String key() { return key; }
    public String encode(Object value) {
        if (type.equals("boolean")) { if (!(value instanceof Boolean)) throw invalid(); return value.toString(); }
        if (type.equals("choice")) { if (!Set.of("left","right").contains(value)) throw invalid(); return "\""+value+"\""; }
        if (!(value instanceof Integer number) || number<min || number>max || this==MAX_FPS&&number%10!=0) throw invalid();
        if (this==FOV) return Double.toString((number-70)/40.0);
        if (this==MOUSE_SENSITIVITY) return Double.toString(number/200.0);
        if (this==MASTER_VOLUME) return Double.toString(number/100.0);
        return number.toString();
    }
    public Map<String,Object> view() { return Map.of("key",key,"label",label,"category",category,"type",type,"min",min,"max",max,"step",this==MAX_FPS?10:1,"defaultValue",defaultValue); }
    private IllegalArgumentException invalid() { return new IllegalArgumentException(label+"的值或类型无效"); }
    public static Map<String,Object> validate(Map<String,Object> values) {
        Objects.requireNonNull(values); var result=new TreeMap<String,Object>();
        for(var entry:values.entrySet()) {
            var field=Arrays.stream(values()).filter(f->f.key.equals(entry.getKey())).findFirst().orElseThrow(()->new IllegalArgumentException("不支持的游戏设置项"));
            field.encode(entry.getValue()); result.put(entry.getKey(),entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
}
