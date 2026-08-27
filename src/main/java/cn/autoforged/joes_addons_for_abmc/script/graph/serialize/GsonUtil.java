package cn.autoforged.joes_addons_for_abmc.script.graph.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 序列化工具：提供一个“无多态适配器”的普通 Gson，用于序列化纯 POJO
 * （{@code GraphValue}、{@code GraphValueRef} 等），避免与多态适配器互相递归。
 */
public final class GsonUtil {
    public static final Gson LEAF = new GsonBuilder().create();

    private GsonUtil() {
    }
}