package cn.autoforged.joes_addons_for_abmc.script;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 脚本运行时动态值：可表示数字、字符串、实体UUID、物品引用、数组、null。
 * 采用动态类型——变量可随时改变类型与值。
 */
public class ScriptValue {
    public enum Type {
        NULL, NUMBER, STRING, UUID, ITEM, ARRAY
    }

    private final Type type;
    private final double number;
    private final String string;
    private final UUID uuid;
    private final ItemStack item;
    private final List<ScriptValue> array;

    private ScriptValue(Type type, double number, String string, UUID uuid, ItemStack item, List<ScriptValue> array) {
        this.type = type;
        this.number = number;
        this.string = string;
        this.uuid = uuid;
        this.item = item;
        this.array = array;
    }

    public static ScriptValue nullValue() {
        return new ScriptValue(Type.NULL, 0, null, null, null, null);
    }

    public static ScriptValue ofNumber(double v) {
        return new ScriptValue(Type.NUMBER, v, null, null, null, null);
    }

    public static ScriptValue ofString(String v) {
        return new ScriptValue(Type.STRING, 0, v, null, null, null);
    }

    public static ScriptValue ofUuid(UUID v) {
        return new ScriptValue(Type.UUID, 0, null, v, null, null);
    }

    public static ScriptValue ofItem(ItemStack v) {
        return new ScriptValue(Type.ITEM, 0, null, null, v, null);
    }

    public static ScriptValue ofArray(List<ScriptValue> values) {
        return new ScriptValue(Type.ARRAY, 0, null, null,
            null, values != null ? values : new ArrayList<>());
    }

    public static ScriptValue emptyArray() {
        return new ScriptValue(Type.ARRAY, 0, null, null, null, new ArrayList<>());
    }

    public Type type() {
        return type;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isNumber() {
        return type == Type.NUMBER;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public boolean isUuid() {
        return type == Type.UUID;
    }

    public boolean isItem() {
        return type == Type.ITEM;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    /** 转为数字；字符串会尝试解析，失败返回 0。 */
    public double asNumber() {
        if (type == Type.NUMBER) return number;
        if (type == Type.STRING) {
            try {
                return Double.parseDouble(string.trim());
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /** 转为字符串；数字按整数/小数智能格式化。 */
    public String asString() {
        switch (type) {
            case NUMBER:
                return formatNumber(number);
            case STRING:
                return string;
            case UUID:
                return uuid.toString();
            case ITEM:
                return item.isEmpty() ? "" : item.getHoverName().getString();
            case ARRAY:
                return "Array[" + array.size() + "]";
            default:
                return "";
        }
    }

    public UUID asUuid() {
        return type == Type.UUID ? uuid : null;
    }

    public ItemStack asItem() {
        return type == Type.ITEM ? item : ItemStack.EMPTY;
    }

    public List<ScriptValue> asList() {
        return type == Type.ARRAY ? array : new ArrayList<>();
    }

    /** 深拷贝当前值。 */
    public ScriptValue copy() {
        switch (type) {
            case NUMBER:
                return ofNumber(number);
            case STRING:
                return ofString(string);
            case UUID:
                return ofUuid(uuid);
            case ITEM:
                return ofItem(item.copy());
            case ARRAY:
                List<ScriptValue> copied = new ArrayList<>(array.size());
                for (ScriptValue e : array) {
                    copied.add(e.copy());
                }
                return ofArray(copied);
            default:
                return nullValue();
        }
    }

    private static String formatNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && !Double.isNaN(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /** 供命令序列化等外部复用的数字格式化。 */
    public static String formatNumberPublic(double v) {
        return formatNumber(v);
    }

    @Override
    public String toString() {
        return asString();
    }
}