package cn.autoforged.joes_addons_for_abmc.item;

public class StaffClientState {
    public static int furnaceOnTicks = 0;
    // 附魔台权杖：当前模式（false=日常模式，true=疯狂模式），用于 HUD 显示
    public static boolean enchantCrazyMode = false;
    // Him 权杖：当前模式（false=近战模式，true=远程模式），用于 HUD 提示
    public static boolean herobrineRanged = false;
    // 命令方块权杖：当前能力模式（0=无，1=击杀，2=抓取，3=启用/禁用AI，4=护盾），用于屏幕下方提示
    public static int commandStaffMode = 0;
    // 命令方块权杖：切换能力后提示文字的剩余显示时长（刻），归零后淡出
    public static int commandStaffModeFlashTicks = 0;
    // 酿造台权杖：药瓶/药水云模式（0=药瓶，1=药水云），由左键切换
    public static int brewingStaffForm = 0;
    // 酿造台权杖：buff/debuff/变形模式（0=buff，1=debuff，2=变形），由Alt+滚轮循环
    public static int brewingStaffCategory = 0;
    // 音符盒权杖：当前模式（0=音符模式，1=音谱模式），由 Alt+滚轮切换，用于 HUD 与左键行为
    public static int noteStaffMode = 0;

    /** 客户端已知的音谱（谱子）数据：ownerUUID → 谱子（5 线端点 + 剩余存在刻数），由服务端广播驱动。 */
    public static final java.util.Map<java.util.UUID, ClientNoteSheet> noteSheets =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 客户端谱子：5 条线（每条 6 个 float：起点/终点 xyz，共 30 个）+ 剩余存在刻数。 */
    public static final class ClientNoteSheet {
        public final float[] segments;
        public int remainingTicks;
        public ClientNoteSheet(float[] segments, int remainingTicks) {
            this.segments = segments;
            this.remainingTicks = remainingTicks;
        }
    }

    /** 应用服务端广播的谱子状态：active=true 时新增/替换对应谱子，false 时移除。 */
    public static void applyNoteSheetState(java.util.UUID owner, boolean active,
                                           float[] segments, int remainingTicks) {
        if (active && segments != null) {
            noteSheets.put(owner, new ClientNoteSheet(segments, remainingTicks));
        } else {
            noteSheets.remove(owner);
        }
    }

    // 附魔台权杖“自体附魔”（空手生物）的持久客户端状态：被自体附魔的实体 id 集合，
    // 由 EnchantSelfPayload（服务端→客户端）驱动。EnchantGlintLayer 据此在材质上持续叠附魔光效，
    // 而非仅在瞄准瞬间显示。
    private static final java.util.Set<Integer> ENCHANT_SELF_IDS =
        java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();

    public static boolean isEnchantSelf(int entityId) {
        return ENCHANT_SELF_IDS.contains(entityId);
    }

    public static void setEnchantSelf(int entityId, boolean enchanted) {
        if (enchanted) {
            ENCHANT_SELF_IDS.add(entityId);
        } else {
            ENCHANT_SELF_IDS.remove(entityId);
        }
    }

    /** 清空所有自附魔状态（登出存档/服务器时调用，避免跨世界残留）。 */
    public static void clearEnchantSelf() {
        ENCHANT_SELF_IDS.clear();
    }
    // Omega 权杖：生存/冒险模式中键拆解被拒绝时的提示显示时长（毫秒，5 秒）
    public static final long OMEGA_DISMANTLE_FORBIDDEN_DURATION_MS = 5000L;
    // Omega 权杖：生存/冒险模式中键拆解被拒绝时的提示截止时间戳（System.currentTimeMillis() 毫秒）。
    // 用墙钟时间而非逐刻递减，避免观看回放（Replay Mod）时客户端 tick 暂停导致提示永不消失。
    public static long omegaDismantleForbiddenUntil = 0L;
}
