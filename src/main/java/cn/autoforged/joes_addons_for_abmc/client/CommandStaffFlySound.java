package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.StaffItem;
import cn.autoforged.joes_addons_for_abmc.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 命令方块权杖的飞行音效（<b>客户端本地音效</b>）。
 *
 * <h2>为什么搬到客户端</h2>
 * 这个音效原本由服务端播（{@code ModMain#handleCommandStaffFlight} 里
 * {@code player.level().playSound(null, ...)}），于是它的开关
 * {@link ModConfig#COMMAND_FLY_SOUND} 只能待在 common 配置里 —— 服务端一读客户端配置就抛异常。
 * 用户要求把这个开关收进 client 配置的 {@code [sound]} 分区，所以音效本身也一起搬到了客户端：
 * 服务端那段现在只负责"发/收飞行能力"，声音由这里本地播。
 * <p>
 * 副作用（有意为之）：声音只给<b>自己</b>听，别的玩家不再听到你的权杖飞行音 —— 与另外两条
 * 客户端音效（鸣钟耳鸣、红石激光）的口径一致。
 *
 * <h2>行为</h2>
 * 手持命令方块权杖 + 正在飞行（{@code abilities.flying}）时，每 {@value #INTERVAL_TICKS} 刻播一次；
 * 条件不满足（放下权杖、落地、关掉开关、创造/旁观）就停手。原服务端版本用的是
 * {@code tickCount % 5}，这里换成自己的计数器，效果一致。
 * <p>
 * 创造/旁观者不播：与原来一致 —— 那段服务端逻辑对创造/旁观直接 return（根本不给飞行能力）。
 */
public final class CommandStaffFlySound {

    /** 两次音效之间的间隔（刻）：5 刻（与原服务端实现一致）。 */
    private static final int INTERVAL_TICKS = 5;

    /** 音量 / 音高：与原服务端 {@code playSound(..., 0.5F, 1.0F)} 一致。 */
    private static final float VOLUME = 0.5F;
    private static final float PITCH = 1.0F;

    private static int tickCounter;

    private CommandStaffFlySound() {
    }

    /** 每客户端刻调用一次（见 {@code ClientEvents.ClientTickHandler#onClientTickPost}）。 */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            tickCounter = 0;
            return;
        }
        if (!ModConfig.COMMAND_FLY_SOUND.get() || !isFlyingWithCommandStaff(mc)) {
            tickCounter = 0;
            return;
        }
        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        mc.getSoundManager().play(new FlySound());
    }

    /** 本地玩家此刻是不是"手持命令方块权杖 + 正在飞行"（创造/旁观不播，理由见类注释）。 */
    private static boolean isFlyingWithCommandStaff(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!player.getAbilities().flying) {
            return false;
        }
        return isCommandStaff(player.getMainHandItem()) || isCommandStaff(player.getOffhandItem());
    }

    private static boolean isCommandStaff(ItemStack stack) {
        return stack.getItem() instanceof StaffItem
            && "command_block".equals(stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /**
     * 飞行音效：相对播放（跟着听者走、不衰减），只给本地玩家听，所以音量不需要按距离折算。
     */
    private static class FlySound extends AbstractSoundInstance {
        FlySound() {
            super(ModSounds.STAFF_COMMAND_FLY.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.volume = VOLUME;
            this.pitch = PITCH;
            this.looping = false;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }
    }
}
