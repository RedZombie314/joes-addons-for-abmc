package cn.autoforged.joes_addons_for_abmc.jade;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * Jade 联动：玩家空壳被 Jade 对准时，在信息框里额外渲染一行<b>来源</b>
 * （附体事件 / 同化事件 / 刷怪蛋 / 变形药水，见 {@link PlayerShellEntity.ShellOrigin}）。
 * <p>
 * 与女巫Boss 那条阶段提示（{@link WitchBossJadePlugin}）同一个套路：<b>来源只存在于服务端</b>
 * （{@link PlayerShellEntity#getShellOrigin()} 是纯服务端字段，不跨端同步，客户端拿不到），
 * 所以必须走 Jade 的服务端数据 Provider（{@link PlayerShellOriginServerDataProvider}）
 * 把它写进同步 tag，客户端 Provider（{@link PlayerShellOriginProvider}）再从
 * {@code accessor.getServerData()} 读出来渲染。
 * <p>
 * 本类仅由 Jade 的 {@code @WailaPlugin} 注解扫描加载（Jade 未安装时不会被引用/加载），
 * Jade 依赖仅为 compileOnly，不打包进本 mod。
 */
@WailaPlugin(ModMain.MODID)
public class PlayerShellJadePlugin implements IWailaPlugin {

    /** 本条提示的 UID（服务端 / 客户端两个 Provider 共用）。 */
    public static final ResourceLocation PLAYER_SHELL_ORIGIN =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "player_shell_origin");

    /** 同步 tag 的键：{@link PlayerShellEntity.ShellOrigin} 的枚举名。 */
    private static final String ORIGIN_KEY = "ShellOrigin";

    /**
     * 文案键前缀：{@code jade.joes_addons_for_abmc.player_shell_origin.<枚举名小写>}，
     * 四个取值分别对应 zh_cn / en_us 里的四条（见 lang 文件）。
     */
    private static final String LANG_PREFIX = "jade.joes_addons_for_abmc.player_shell_origin.";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(new PlayerShellOriginServerDataProvider(), PlayerShellEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new PlayerShellOriginProvider(), PlayerShellEntity.class);
    }

    /** 服务端：把"这具空壳是哪个入口造出来的"写入同步数据 tag。 */
    public static class PlayerShellOriginServerDataProvider implements IServerDataProvider<EntityAccessor> {
        @Override
        public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof PlayerShellEntity shell)) {
                return;
            }
            tag.putString(ORIGIN_KEY, shell.getShellOrigin().name());
        }

        @Override
        public ResourceLocation getUid() {
            return PLAYER_SHELL_ORIGIN;
        }
    }

    /**
     * 客户端：从同步数据 tag 读出来源并渲染一行。
     * <p>
     * 优先级 -4400，与女巫Boss 那条一致：紧跟在原版血量条（-4500）之后，也就是显示在血量下方。
     */
    public static class PlayerShellOriginProvider implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (serverData == null || !serverData.contains(ORIGIN_KEY)) {
                return;
            }
            tooltip.add(originLine(serverData.getString(ORIGIN_KEY)));
        }

        @Override
        public ResourceLocation getUid() {
            return PLAYER_SHELL_ORIGIN;
        }

        @Override
        public int getDefaultPriority() {
            return -4400;
        }
    }

    /**
     * 枚举名 → 一行文案。
     * <p>
     * {@code TRANSMUTED}（变形药水造的临时壳）<b>也照实显示</b>：它不是"由幸运核心支撑"的那三类，
     * 但"什么都不显示"会让人以为这具壳是普通的、看不出它其实是个变形产物。
     * <p>
     * 认不出来的取值（以后新增了枚举、而这条提示没跟上）原样显示枚举名，不吞掉信息。
     */
    private static Component originLine(String originName) {
        PlayerShellEntity.ShellOrigin origin;
        try {
            origin = PlayerShellEntity.ShellOrigin.valueOf(originName);
        } catch (IllegalArgumentException unknown) {
            return Component.literal(originName);
        }
        return Component.translatable(LANG_PREFIX + origin.name().toLowerCase(Locale.ROOT));
    }
}
