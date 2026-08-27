package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 节点图节点的类型判别符（JSON 持久化时作为 {@code type} 字段）。
 * <p>
 * 每个值大致对应一个 {@link ScriptGraphNode} 具体子类：
 * <ul>
 *   <li>流程控制：COMMAND / IF / LOOP / WAIT / BREAK / EVENT_SEND / EVENT_RECEIVE / FUNCTION_CALL</li>
 *   <li>数据操作：VAR_GET / VAR_SET / ARRAY_OP / SET_OP / CONVERT / VALUE_SOURCE</li>
 * </ul>
 */
public enum GraphNodeType {
    COMMAND,
    IF,
    LOOP,
    WAIT,
    BREAK,
    EVENT_SEND,
    EVENT_RECEIVE,
    FUNCTION_CALL,
    VAR_GET,
    VAR_SET,
    ARRAY_OP,
    SET_OP,
    CONVERT,
    VALUE_SOURCE,
    PROGRAM_ENTRY
}