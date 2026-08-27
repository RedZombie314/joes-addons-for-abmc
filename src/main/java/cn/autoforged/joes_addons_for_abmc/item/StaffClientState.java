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
    // Omega 权杖：生存/冒险模式中键拆解被拒绝时的提示显示时长（刻）
    public static final int OMEGA_DISMANTLE_FORBIDDEN_DURATION = 100;
    // Omega 权杖：生存/冒险模式中键拆解被拒绝时的提示剩余显示时间（刻）
    public static int omegaDismantleForbiddenTicks = 0;
}
