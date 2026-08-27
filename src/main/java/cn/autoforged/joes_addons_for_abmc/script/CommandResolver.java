package cn.autoforged.joes_addons_for_abmc.script;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令占位符解析：把命令模板中的 $(变量名) 替换为变量当前值的命令序列化表示。
 * <p>
 * 规则：
 * <ul>
 *   <li>\$(xxx) 转义为字面量 $(xxx)，不做替换；</li>
 *   <li>变量不存在或为 null → 空字符串；</li>
 *   <li>数字 → 整数/小数格式化；字符串 → 原样；UUID → UUID 字符串；</li>
 *   <li>物品 → 注册名（如 minecraft:diamond_sword）；数组 → 空格拼接各元素。</li>
 * </ul>
 */
public final class CommandResolver {
    private CommandResolver() {
    }

    public static String resolve(String template, VariableScope scope) {
        if (template == null || template.isEmpty()) return template;
        StringBuilder sb = new StringBuilder(template.length());
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            // 转义形式：\$( -> 字面量 $(
            if (c == '\\' && i + 2 < n && template.charAt(i + 1) == '$' && template.charAt(i + 2) == '(') {
                sb.append("$(");
                i += 3;
                continue;
            }
            // 占位符：$(name)
            if (c == '$' && i + 1 < n && template.charAt(i + 1) == '(') {
                int close = template.indexOf(')', i + 2);
                if (close >= 0) {
                    String name = template.substring(i + 2, close).trim();
                    sb.append(serialize(scope.get(name)));
                    i = close + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    public static String serialize(ScriptValue v) {
        if (v == null || v.isNull()) return "";
        switch (v.type()) {
            case NUMBER:
                return ScriptValue.formatNumberPublic(v.asNumber());
            case STRING:
                return v.asString();
            case UUID:
                return v.asUuid().toString();
            case ITEM:
                return itemId(v.asItem());
            case ARRAY: {
                List<String> parts = new ArrayList<>(v.asList().size());
                for (ScriptValue e : v.asList()) {
                    parts.add(serialize(e));
                }
                return String.join(" ", parts);
            }
            default:
                return "";
        }
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}