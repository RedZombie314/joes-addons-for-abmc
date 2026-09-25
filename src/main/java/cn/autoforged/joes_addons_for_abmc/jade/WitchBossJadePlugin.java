package cn.autoforged.joes_addons_for_abmc.jade;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Witch;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 联动：女巫Boss 被 Jade 对准时，在血量条下方额外渲染一行当前阶段文字。
 * <p>阶段信息存放在女巫的 PersistentData 中，**不会同步到客户端**，因此必须走 Jade 的
 * 服务端数据 Provider（{@link WitchBossStageServerDataProvider}）把阶段写入同步 tag，
 * 客户端 Provider 再从 {@code accessor.getServerData()} 读取渲染。</p>
 * <p>本类仅由 Jade 的 {@code @WailaPlugin} 注解扫描加载（Jade 未安装时不会被引用/加载），
 * Jade 依赖仅为 compileOnly，不打包进本 mod。</p>
 */
@WailaPlugin(ModMain.MODID)
public class WitchBossJadePlugin implements IWailaPlugin {

    public static final ResourceLocation WITCH_BOSS_STAGE =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "witch_boss_stage");
    private static final String STAGE_KEY = "WitchBossStage";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(new WitchBossStageServerDataProvider(), Witch.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new WitchBossStageProvider(), Witch.class);
    }

    /** 服务端：把女巫Boss当前阶段写入同步数据 tag（客户端拿不到 PersistentData，只能靠这里）。 */
    public static class WitchBossStageServerDataProvider implements IServerDataProvider<EntityAccessor> {
        @Override
        public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof Witch witch)) return;
            if (!witch.getPersistentData().getBoolean(ModMain.WITCH_BOSS_TAG)) return;
            tag.putInt(STAGE_KEY, ModMain.getWitchBossStage(witch));
        }

        @Override
        public ResourceLocation getUid() {
            return WITCH_BOSS_STAGE;
        }
    }

    /** 客户端：从同步数据 tag 读取阶段并渲染一行文字。优先级 -4400，紧跟在原版血量条（-4500）之后，即显示在 HP 下方。 */
    public static class WitchBossStageProvider implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag serverData = accessor.getServerData();
            if (serverData == null || !serverData.contains(STAGE_KEY)) return;
            tooltip.add(Component.literal(ModMain.witchBossStageName(serverData.getInt(STAGE_KEY))));
        }

        @Override
        public ResourceLocation getUid() {
            return WITCH_BOSS_STAGE;
        }

        @Override
        public int getDefaultPriority() {
            return -4400;
        }
    }
}
