package cn.autoforged.joes_addons_for_abmc.script.cond;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;

/** 条件真值判断辅助。 */
public final class Truthiness {
    private Truthiness() {
    }

    public static boolean isTruthy(ScriptValue v) {
        if (v == null || v.isNull()) return false;
        switch (v.type()) {
            case NUMBER:
                return v.asNumber() != 0;
            case STRING:
                return !v.asString().isEmpty();
            case UUID:
                return v.asUuid() != null;
            case ITEM:
                return !v.asItem().isEmpty();
            case ARRAY:
                return !v.asList().isEmpty();
            default:
                return false;
        }
    }
}