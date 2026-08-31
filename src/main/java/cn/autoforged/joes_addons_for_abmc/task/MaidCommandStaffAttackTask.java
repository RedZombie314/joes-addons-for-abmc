package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 女仆“权杖攻击”任务中，命令方块权杖（command_block）的专属行为。
 * 主人把权杖设为某模式后移交女仆，女仆按其主人 PersistentData 中的当前模式行为：
 * <ul>
 *   <li>击杀模式(1)：瞄准附近的敌对生物，模拟左键用 kill 命令击杀，并在女仆与目标之间渲染 END_ROD 连线、头顶渲染对应命令文本；</li>
 *   <li>启用/禁用 AI 模式(3)：瞄准附近的敌对生物，对其 NoAI 取反（通常为设为 1b，禁用 AI），同样渲染连线与命令文本。</li>
 *       执行后该目标已为 NoAI，行为停止对其重复索敌/重复刷新文本（每个新目标只 toggle 一次）；</li>
 *   <li>护盾模式(4)：半径 7 格，对投掷物做与玩家一致的镜面反射/斥力，并弹开点燃 TNT/苦力怕，头顶渲染一次护盾命令文本；</li>
 *   <li>抓取模式(2) / 无模式(0)：女仆不主动索敌、无行为。</li>
 * </ul>
 * 头顶命令文本沿用玩家提供的原始命令格式：
 * 击杀 → {@code /kill <UUID>}；启用/禁用 AI → {@code /data modify entity <UUID> NoAI set value 0/1}；
 * 护盾 → {@code /attribute @p generic.shield_size base set 7}（与玩家命令方块权杖的显示一致，不自行编造格式）。
 */
public class MaidCommandStaffAttackTask extends Behavior<EntityMaid> {

    private static final String COMMAND_STAFF_MODE_TAG = "jafa_command_staff_mode";
    private static final int MODE_NONE = 0;
    private static final int MODE_KILL = 1;
    private static final int MODE_GRAB = 2;
    private static final int MODE_TOGGLE_AI = 3;
    private static final int MODE_SHIELD = 4;

    private static final double SHIELD_RADIUS = 7.0D;
    private static final double SHIELD_DETECT_MARGIN = 6.0D;

    /** 护盾指令文本（与玩家命令方块权杖一致的显示格式）。 */
    private static final String SHIELD_TEXT = "/attribute @p generic.shield_size base set 7";

    /** 本行为实例是否已渲染过护盾命令文本。护盾为持续反射，只在进入护盾模式时显示一次文本，
     *  到期（约 20 刻）自动消失，避免每次行为重启都重复刷新而“一直显示”。 */
    private boolean shieldTextShown = false;

    public MaidCommandStaffAttackTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
            20);
    }

    public static boolean isCommandStaff(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItems.STAFF.get())) return false;
        return "command_block".equals(stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 读取主人设置的模式（无主人或非玩家 → 无模式）；1=击杀、3=启用/禁用AI、4=护盾。 */
    public static int getMode(EntityMaid maid) {
        Entity owner = maid.getOwner();
        if (owner instanceof ServerPlayer sp) {
            return sp.getPersistentData().getInt(COMMAND_STAFF_MODE_TAG);
        }
        return 0;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!isCommandStaff(maid.getMainHandItem())) return false;
        int mode = getMode(maid);
        if (mode == MODE_SHIELD) return true;
        if (mode != MODE_KILL && mode != MODE_TOGGLE_AI) return false;
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) return false;
        if (mode == MODE_TOGGLE_AI) {
            // 目标已被禁 AI（本行为处理过）则不再启动：避免持续索敌同一目标、反复刷新头顶文本。
            return target instanceof Mob mob && !mob.isNoAi();
        }
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!isCommandStaff(maid.getMainHandItem())) return false;
        int mode = getMode(maid);
        if (mode == MODE_SHIELD) return true; // 护盾常驻
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) return false;
        if (mode == MODE_KILL) return true; // 击杀：直到目标死亡
        if (mode == MODE_TOGGLE_AI) {
            // 目标已被禁 AI 后即停止，避免持续锁定同一目标（每个目标只 toggle 一次）。
            return target instanceof Mob mob && !mob.isNoAi();
        }
        return false;
    }

    /** 击杀/AI/护盾模式在启动时执行一次行为（模拟一次左键）：渲染连线与头顶命令文本；护盾模式在 tick 中持续反弹。 */
    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        int mode = getMode(maid);
        if (mode == MODE_SHIELD) {
            if (!shieldTextShown) {
                ModMain.spawnMaidFloatingText(level, maid, SHIELD_TEXT);
                shieldTextShown = true;
            }
            return;
        }
        if (mode != MODE_KILL && mode != MODE_TOGGLE_AI) return;
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) return;
        BehaviorUtils.lookAtEntity(maid, target);
        spawnLineParticles(level, maid.getEyePosition(), target.getEyePosition());

        net.minecraft.commands.CommandSourceStack source =
            level.getServer().createCommandSourceStack().withSuppressedOutput();
        if (mode == MODE_KILL) {
            // 与玩家击杀模式一致的命令文本：/kill <UUID>
            String cmd = "kill " + target.getStringUUID();
            ModMain.spawnMaidFloatingText(level, maid, "/" + cmd);
            level.getServer().getCommands().performPrefixedCommand(source, cmd);
        } else if (mode == MODE_TOGGLE_AI) {
            // 与玩家 toggleCommandStaffNoAi 一致：读当前 NoAI 取反；Mob 用 setNoAi（可靠写入存档），
            // 其它实体通过 /data modify entity ... 指令切换。头顶文本采用玩家原始格式 /data modify entity ...（不带字节后缀 b）。
            boolean newVal;
            if (target instanceof Mob mob) {
                newVal = !mob.isNoAi();
                mob.setNoAi(newVal);
            } else {
                CompoundTag tag = target.saveWithoutId(new CompoundTag());
                newVal = !tag.getBoolean("NoAI");
                String cmd = String.format("data modify entity %s NoAI set value %db",
                    target.getStringUUID(), newVal ? 1 : 0);
                level.getServer().getCommands().performPrefixedCommand(source, cmd);
            }
            ModMain.spawnMaidFloatingText(level, maid,
                String.format("/data modify entity %s NoAI set value %d", target.getStringUUID(), newVal ? 1 : 0));
        }
    }

    /** 护盾模式：每刻对女仆周围投掷物做镜面反射/斥力、弹开点燃 TNT/苦力怕（与玩家一致，半径 7）。 */
    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (getMode(maid) != MODE_SHIELD) return;
        Vec3 center = maid.position();
        AABB box = maid.getBoundingBox().inflate(SHIELD_RADIUS + SHIELD_DETECT_MARGIN);
        for (Entity e : level.getEntities(maid, box,
                ent -> ent.isAlive()
                    && (ent instanceof Projectile
                        || ent instanceof PrimedTnt
                        || ent instanceof Creeper))) {
            if (e instanceof Projectile proj) {
                if (proj.getOwner() == maid) continue;
                Vec3 vel = proj.getDeltaMovement();
                double speed = vel.length();
                if (speed < 1.0E-4) continue;
                Vec3 projPos = proj.position();
                Vec3 rel = projPos.subtract(center);
                double dist = rel.length();
                if (dist < 1.0E-4) continue;
                if (dist < SHIELD_RADIUS) {
                    // 球内置成斥力（与玩家一致）
                    if (rel.dot(vel) > 0.0) continue;
                    Vec3 radialOut = rel.scale(1.0 / dist);
                    double vIn = -rel.dot(vel) / dist;
                    double strength = 0.5 * (1.0 - dist / SHIELD_RADIUS);
                    if (strength < 0.0) strength = 0.0;
                    double vOut = vIn + strength;
                    proj.setDeltaMovement(vel.add(radialOut.scale(vIn + vOut)));
                    proj.hasImpulse = true;
                    continue;
                }
                // 球外镜面反射（与玩家一致）
                if (rel.dot(vel) >= 0.0) continue;
                double a = vel.lengthSqr();
                double b = 2.0 * rel.dot(vel);
                double c = dist * dist - SHIELD_RADIUS * SHIELD_RADIUS;
                double disc = b * b - 4.0 * a * c;
                if (disc < 0.0) continue;
                double tEnter = (-b - Math.sqrt(disc)) / (2.0 * a);
                if (tEnter < 0.0 || tEnter > 1.0) continue;
                Vec3 hit = projPos.add(vel.scale(tEnter));
                Vec3 normal = hit.subtract(center).normalize();
                Vec3 reflected = vel.subtract(normal.scale(2.0 * vel.dot(normal)));
                proj.setDeltaMovement(reflected);
                proj.hasImpulse = true;
                proj.setPos(hit.x + normal.x * 0.05, hit.y + normal.y * 0.05, hit.z + normal.z * 0.05);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    hit.x, hit.y, hit.z, 3, 0.1, 0.1, 0.1, 0.05);
            } else {
                // 点燃 TNT / 苦力怕：径向弹开
                Vec3 pos = e.position();
                Vec3 rel = pos.subtract(center);
                double dist = rel.length();
                if (dist < 1.0E-4) continue;
                double speed = e.getDeltaMovement().length();
                if (speed < 1.0E-4) continue;
                Vec3 radialOut = rel.scale(1.0 / dist);
                e.setDeltaMovement(radialOut.scale(Math.max(0.6, speed + 0.3)));
                e.hasImpulse = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private LivingEntity getAttackTarget(EntityMaid maid) {
        return (LivingEntity) maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /** 与玩家一致：在起点→终点之间渲染 END_ROD 粒子连线（每 0.4 格一个）。 */
    private static void spawnLineParticles(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double dist = d.length();
        if (dist < 0.1) return;
        int perLine = 0;
        for (double d0 = 0.4; d0 <= dist && perLine < 256; d0 += 0.4) {
            Vec3 p = from.lerp(to, d0 / dist);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
            perLine++;
        }
    }
}