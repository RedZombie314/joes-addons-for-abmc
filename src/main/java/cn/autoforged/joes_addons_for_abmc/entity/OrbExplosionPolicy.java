package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.Nullable;

/**
 * <b>附体体系的爆炸不破坏地形</b>：苦力怕弹、爆裂猫、爆裂僵尸、恶魂火球、以及己方凋灵
 * （含它的凋灵之首和"护甲爆发"）炸开时都不会动一个方块 —— <b>哪怕 {@code mobGriefing} 为 true</b>。
 * 另外还有一条<b>实体</b>规则：<b>我们自己发射的爆炸弹之间互不伤害</b>（见
 * {@link #onExplosionDetonate} 的第二段）。
 *
 * <h2>怎么做到"爆炸但不动地形"</h2>
 * 原版爆炸的方块破坏清单是先在 {@code Explosion} 里算好、再交给
 * {@link ExplosionEvent.Detonate} 让模组修改的，之后才真正破坏。所以这里只做一件事：
 * 认出"这是我们体系造成的爆炸"就把那份清单<b>清空</b> —— 实体伤害、击退、音效、粒子照旧，
 * 一个方块都不掉。
 *
 * <p>只清方块清单，不取消爆炸本身（{@code ExplosionEvent.Start} 一取消连伤害都没了，那是另一个语义）。
 *
 * <h2>怎么认出"我们的爆炸"</h2>
 * <ol>
 *   <li>实体带 {@link #TAG_NO_TERRAIN_GRIEF} 标记（需要显式声明的一次性弹体，例如恶魂火球）；</li>
 *   <li>实体本身是附体召唤物（苦力怕弹/爆裂猫/爆裂僵尸/己方凋灵 —— 它们生成时都打过召唤标记）；</li>
 *   <li>弹射物的<b>主人</b>是附体召唤物（己方凋灵吐出的凋灵之首就是这么认出来的 ——
 *       那玩意儿是原版凋灵自己造的，我们插不进手去打标记）。</li>
 * </ol>
 *
 * <p><b>不在管辖范围内的</b>：玩家自己用 TNT 权杖扔出去的那只苦力怕、权杖的 TNT、
 * 以及模组里其它与附体无关的爆炸 —— 它们照旧按原版规则破坏地形。
 */
@EventBusSubscriber(modid = ModMain.MODID)
public final class OrbExplosionPolicy {

    /** 显式标记：这颗弹体炸开时不动地形。 */
    private static final String TAG_NO_TERRAIN_GRIEF = "jafa_orb_no_terrain_grief";

    private OrbExplosionPolicy() {
    }

    /** 给实体打上"炸开不动地形"的标记（随实体存盘，读档后依然有效）。 */
    public static void markNoTerrainGrief(Entity entity) {
        entity.getPersistentData().putBoolean(TAG_NO_TERRAIN_GRIEF, true);
    }

    /** 这次爆炸的来源实体是不是"属于附体体系、不该破坏地形"的那一类。 */
    public static boolean protectsTerrain(@Nullable Entity source) {
        if (source == null) {
            return false;
        }
        if (source.getPersistentData().getBoolean(TAG_NO_TERRAIN_GRIEF)) {
            return true;
        }
        if (OrbPossessionSummons.isSummon(source)) {
            return true;
        }
        // 凋灵之首这类"召唤物造出来的弹射物"：看它的主人
        return source instanceof Projectile projectile
            && OrbPossessionSummons.isSummon(projectile.getOwner());
    }

    /**
     * 是不是<b>我们自己发射的那种爆炸弹</b>（多重射击会一次发出好几颗同型号的）。
     * <p>
     * 与 {@code FlyingBombControl#isExplosiveEntity} 的区别：那个是"一切爆炸性实体"（含原版苦力怕/TNT），
     * 用来判"贴到一起要不要引爆"；这里是"我们自己造的"，只用来判"要不要免掉彼此的爆炸伤害" ——
     * 原版苦力怕、原版 TNT 当然照旧会被我们的爆炸炸到。
     */
    private static boolean isOurExplosive(@Nullable Entity entity) {
        return entity instanceof TntStaffCreeper          // 苦力怕弹（含权杖那只抛掷的）
            || entity instanceof OrbExplosiveCat
            || entity instanceof OrbExplosiveZombie
            || entity instanceof OrbExplosiveSlime
            || entity instanceof OrbExplosiveSkeleton
            || entity instanceof TntStaffPrimedTnt;       // 权杖丢出的特制 TNT
    }

    /**
     * 爆炸结算前改两份清单：
     * <ol>
     *   <li><b>方块破坏清单</b>：属于附体体系的爆炸 → 清空（见类注释）；</li>
     *   <li><b>受影响实体清单</b>：<b>我们自己发射的爆炸弹之间互不伤害</b>（用户指定规则的延伸）。
     *       多重射击下一发就是 3~7 颗，第一颗炸响时后面几颗往往就在旁边 —— 不去掉的话它们会被
     *       直接炸死（那是"凭空消失"，不是"各自飞过去炸开"）。只保护自己人造的爆炸弹，
     *       玩家、生物、原版苦力怕/TNT 照旧受伤。</li>
     * </ol>
     * 事件本身不可取消（那是 {@code Start} 的事），但两份清单返回的都是即将生效的可变列表，可以直接改。
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity direct = event.getExplosion().getDirectSourceEntity();
        Entity indirect = event.getExplosion().getIndirectSourceEntity();
        if (protectsTerrain(direct) || protectsTerrain(indirect)) {
            event.getAffectedBlocks().clear();
        }
        // 爆炸性实体之间互不伤害：把自己人造的爆炸弹从"受影响实体"里摘掉（爆炸源自己除外，
        // 它本来就该在这次爆炸里消失）。
        if (isOurExplosive(direct) || isOurExplosive(indirect)) {
            event.getAffectedEntities().removeIf(e -> e != direct && isOurExplosive(e));
        }
    }
}
