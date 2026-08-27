package cn.autoforged.joes_addons_for_abmc.client.editor;

/**
 * E 图形编辑器画布状态：视图缩放/平移、选中节点、当前拖拽类型与连线源。
 * 纯客户端状态，不参与数据持久化。
 */
public class GraphEditorState {

    public enum Drag {
        /** 无拖拽。 */
        NONE,
        /** 拖拽空白平移画布。 */
        PAN,
        /** 拖动节点（改变位置）。 */
        MOVE_NODE,
        /** 从逻辑输出端口拉连线。 */
        LOGIC_LINK,
        /** 从值输出端口拉连线。 */
        VALUE_LINK
    }

    public double zoom = 1.0;
    public double panX = 0;
    public double panY = 0;

    public String selectedNodeId;

    public Drag drag = Drag.NONE;

    // 拖动节点
    public String moveNodeId;
    public double moveOffsetX;
    public double moveOffsetY;

    // 拉连线（源）
    public String linkSourceNode;
    public String linkSourcePort;
    public boolean linkIsValue;

    // 平移起始
    public double panStartX;
    public double panStartY;

    /** 鼠标在画布世界坐标（未缩放平移）。 */
    public double mouseWorldX;
    public double mouseWorldY;

    /** 屏幕坐标 → 世界坐标。 */
    public double toWorldX(double sx) {
        return (sx - panX) / zoom;
    }

    public double toWorldY(double sy) {
        return (sy - panY) / zoom;
    }

    /** 世界坐标 → 屏幕坐标。 */
    public double toScreenX(double wx) {
        return wx * zoom + panX;
    }

    public double toScreenY(double wy) {
        return wy * zoom + panY;
    }
}